package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.WorkflowApprovalContextDto;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the approval-chain lifecycle for workflow items of type 'approval' (see FEAT-06 plan).
 * Split out of WorkflowService rather than bloating that class further, mirroring how
 * SlaTimerService/SlaEscalationExecutor were split out of TicketService for the same reason.
 *
 * Level *definitions* (who approves, timeout) live in the item's typeConfig, set at template-design
 * time — {@code {"levels": [{"approverType": "specific_user"|"group", "approverUserId": N,
 * "approverGroupId": N, "timeoutHours": N}, ...]}}. This service walks those levels at runtime,
 * recording one WorkflowApprovalDecisionEntity row per level actually reached.
 *
 * "Line manager auto-detect" (Mock 1's "Approver: Line Manager (auto-detect)") is NOT implemented —
 * no manager/reporting-line concept exists anywhere in this schema (confirmed absent during the SLA
 * feature's own escalation-target design, same gap). Only 'specific_user' and 'group' approver
 * types are supported, matching that same precedent's resolution (explicit admin-picked target).
 */
@Slf4j
@Service
public class ApprovalService {

    private static final int TOKEN_VALID_DAYS = 7; // matches TicketCsatEntity's own expiresAt window

    private final WorkflowApprovalDecisionRepository decisionRepo;
    private final WorkflowApprovalTokenRepository tokenRepo;
    private final WorkflowItemRepository itemRepo;
    private final WorkflowService workflowService;
    private final NotificationService notificationService;
    private final AdminInboxService adminInboxService;
    private final TicketRepository ticketRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final UserRepository userRepo;
    private final NotificationTemplateRepository templateRepo;
    private final EmailSenderService emailSenderService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public ApprovalService(WorkflowApprovalDecisionRepository decisionRepo, WorkflowApprovalTokenRepository tokenRepo,
                            WorkflowItemRepository itemRepo, WorkflowService workflowService,
                            NotificationService notificationService, AdminInboxService adminInboxService,
                            TicketRepository ticketRepo, GroupMemberRepository groupMemberRepo,
                            UserRepository userRepo, NotificationTemplateRepository templateRepo,
                            EmailSenderService emailSenderService) {
        this.decisionRepo = decisionRepo;
        this.tokenRepo = tokenRepo;
        this.itemRepo = itemRepo;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
        this.adminInboxService = adminInboxService;
        this.ticketRepo = ticketRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.userRepo = userRepo;
        this.templateRepo = templateRepo;
        this.emailSenderService = emailSenderService;
    }

    /**
     * Called by WorkflowService when an 'approval' item transitions to in_progress. Creates the
     * level-0 decision row — but only on a genuinely fresh activation. Idempotent by design: if the
     * ticket was suspended mid-chain (e.g. status → waiting) and later resumed, WorkflowService's
     * cascade re-activates this same item, and this must NOT restart the chain from level 0 — the
     * existing pending decision (whatever level it's actually at) is still valid as-is.
     */
    @Transactional
    public void activateApproval(WorkflowItemEntity item) {
        if (!decisionRepo.findByWorkflowItemIdOrderById(item.getId()).isEmpty()) {
            log.debug("[Approval] Item {} already has decision history — resume, not a fresh start", item.getId());
            return;
        }
        List<Map<String, Object>> levels = levelsOf(item);
        if (levels.isEmpty()) {
            log.warn("[Approval] Item {} has type=approval but no levels configured in typeConfig — nothing to activate", item.getId());
            return;
        }
        activateLevel(item, levels, 0);
    }

    /** Resolves the current pending level for an item and records the decision. Public entry point for both the in-app controller (Phase 1) and the one-click email token flow (Phase 2). */
    @Transactional
    public void recordDecision(Long itemId, String decision, String reason, Integer actorUserId) {
        if (!"approved".equals(decision) && !"rejected".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be 'approved' or 'rejected'");
        }
        WorkflowItemEntity item = itemRepo.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow item not found"));
        if (!"approval".equals(item.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item " + itemId + " is not an approval item");
        }
        WorkflowApprovalDecisionEntity pending = decisionRepo.findByWorkflowItemIdAndDecision(itemId, "pending")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pending approval decision for this item"));

        assertCanDecide(pending, actorUserId);

        pending.setDecision(decision);
        pending.setDecidedAt(LocalDateTime.now());
        pending.setDecidedByUserId(actorUserId);
        if ("rejected".equals(decision)) pending.setRejectionReason(reason);
        decisionRepo.save(pending);

        if ("rejected".equals(decision)) {
            resolve(item, "rejected");
            return;
        }

        // approved — advance to the next level, or resolve if this was the last one
        List<Map<String, Object>> levels = levelsOf(item);
        int nextLevel = pending.getLevelOrder() + 1;
        if (nextLevel < levels.size()) {
            activateLevel(item, levels, nextLevel);
        } else {
            resolve(item, "approved");
        }
    }

    public List<WorkflowApprovalDecisionEntity> getDecisions(Long workflowItemId) {
        return decisionRepo.findByWorkflowItemIdOrderById(workflowItemId);
    }

    /**
     * Workflow item IDs with a currently-pending decision assigned to this user, directly or via
     * group membership — used by WorkflowService.getItemsForUser to surface pending approvals in
     * "My Tasks" (FEAT-06 Phase 3). A real gap otherwise: approval items never populate
     * assignedUserId/assignedGroupId (the approver lives in typeConfig/decision rows instead), so
     * getItemsForUser's existing assignedUserId/assignedGroupId lookup alone would never find them.
     */
    public List<Long> getPendingApprovalItemIds(Integer userId, List<Integer> groupIds) {
        List<WorkflowApprovalDecisionEntity> pending = decisionRepo.findByDecision("pending");
        List<Long> ids = new java.util.ArrayList<>();
        for (WorkflowApprovalDecisionEntity d : pending) {
            boolean mine = userId != null && userId.equals(d.getApproverUserId());
            boolean mineViaGroup = d.getApproverGroupId() != null && groupIds != null && groupIds.contains(d.getApproverGroupId());
            if ((mine || mineViaGroup) && !ids.contains(d.getWorkflowItemId())) ids.add(d.getWorkflowItemId());
        }
        return ids;
    }

    /**
     * Guards the in-app decision path (WorkflowController.recordApprovalDecision, session-based)
     * from any authenticated user deciding someone else's pending approval — a real gap that would
     * otherwise open up the moment an in-app Approve/Reject button exists (Phase 3 UI). Deliberately
     * a no-op when actorUserId is null: that only happens via the group-approver one-click email
     * token path (recordDecisionByToken's documented "can't attribute to one member" trade-off,
     * Phase 2), which is intentionally unattributed, not unauthenticated-in-app.
     */
    private void assertCanDecide(WorkflowApprovalDecisionEntity pending, Integer actorUserId) {
        if (actorUserId == null) return;
        Long actorId = actorUserId.longValue();
        if (actorId.equals(pending.getApproverUserId() != null ? pending.getApproverUserId().longValue() : null)) return;
        if (pending.getApproverGroupId() != null
                && groupMemberRepo.existsByGroupIdAndUserId(pending.getApproverGroupId().longValue(), actorId)) return;
        boolean isSuperAdmin = userRepo.findById(actorId).map(UserEntity::isSuperAdmin).orElse(false);
        if (isSuperAdmin) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an approver for this decision");
    }

    // ── ONE-CLICK EMAIL TOKEN FLOW ───────────────────────────────────────────────
    // Clones TicketCsatEntity/CsatService's exact public-token pattern (see FEAT-06 plan) — token
    // one-time-use enforced via usedAt != null, expiry via expiresAt, both checked before any state
    // change. GET (getContextByToken) is read-only/safe-to-prefetch by design; only the POST
    // (recordDecisionByToken, called from a button click on the public page, not directly from the
    // emailed link) actually records anything — deliberately not a bare GET-triggers-mutation link
    // like the mock's buttons visually suggest, since some corporate email scanners prefetch links
    // in emails, which would silently record a decision no human actually made.

    public WorkflowApprovalContextDto getContextByToken(String token) {
        WorkflowApprovalTokenEntity tokenEntity = tokenRepo.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval link not found"));
        WorkflowApprovalDecisionEntity decision = decisionRepo.findById(tokenEntity.getWorkflowApprovalDecisionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval decision not found"));
        WorkflowItemEntity item = itemRepo.findById(decision.getWorkflowItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow item not found"));
        TicketEntity ticket = ticketRepo.findById(item.getTicketId()).orElse(null);
        UserEntity requester = ticket != null && ticket.getRequestUserId() != null
                ? userRepo.findById(ticket.getRequestUserId().longValue()).orElse(null) : null;

        WorkflowApprovalContextDto dto = new WorkflowApprovalContextDto();
        dto.setTicketId(item.getTicketId());
        dto.setTicketTitle(ticket != null ? ticket.getTitle() : null);
        dto.setItemTitle(item.getTitle());
        dto.setRequesterName(requester != null
                ? (requester.getDisplayName() != null ? requester.getDisplayName() : requester.getUsername())
                : "Unknown");
        dto.setLevelOrder(decision.getLevelOrder());
        dto.setExpired(tokenEntity.getExpiresAt().isBefore(LocalDateTime.now()));
        dto.setAlreadyDecided(!"pending".equals(decision.getDecision()));
        if (dto.isAlreadyDecided()) dto.setExistingDecision(decision.getDecision());
        return dto;
    }

    @Transactional
    public void recordDecisionByToken(String token, String decision, String reason) {
        WorkflowApprovalTokenEntity tokenEntity = tokenRepo.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval link not found"));
        if (tokenEntity.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This approval link has already been used");
        }
        if (tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This approval link has expired");
        }
        WorkflowApprovalDecisionEntity decisionRow = decisionRepo.findById(tokenEntity.getWorkflowApprovalDecisionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval decision not found"));

        tokenEntity.setUsedAt(LocalDateTime.now());
        tokenRepo.save(tokenEntity);

        // Best-effort attribution — a specific_user approver is attributed directly; a group
        // approver acting via an anonymous email link can't be attributed to one member without
        // requiring login, which the whole point of a one-click link is to avoid.
        Integer actorUserId = decisionRow.getApproverUserId();
        recordDecision(decisionRow.getWorkflowItemId(), decision, reason, actorUserId);
    }

    private void activateLevel(WorkflowItemEntity item, List<Map<String, Object>> levels, int levelOrder) {
        Map<String, Object> level = levels.get(levelOrder);
        String approverType = String.valueOf(level.get("approverType"));

        WorkflowApprovalDecisionEntity row = new WorkflowApprovalDecisionEntity();
        row.setWorkflowItemId(item.getId());
        row.setLevelOrder(levelOrder);
        if ("group".equals(approverType) && level.get("approverGroupId") != null) {
            row.setApproverGroupId(((Number) level.get("approverGroupId")).intValue());
        } else if (level.get("approverUserId") != null) {
            row.setApproverUserId(((Number) level.get("approverUserId")).intValue());
        }
        decisionRepo.save(row);

        log.info("[Approval] Item {} activated level {} (approverUserId={}, approverGroupId={})",
                item.getId(), levelOrder, row.getApproverUserId(), row.getApproverGroupId());
        notifyApprovers(item, row);
    }

    private void resolve(WorkflowItemEntity item, String outcome) {
        item.setStatus("done");
        itemRepo.save(item);
        log.info("[Approval] Item {} resolved: {}", item.getId(), outcome);
        workflowService.activateChildren(item.getId(), item.getTicketId(), outcome);
    }

    // ── TIMEOUT ESCALATION ────────────────────────────────────────────────────
    // Called by ApprovalEscalationSchedulerService's tick for every currently-pending decision.
    // No "already checked" flag needed to avoid re-triggering on every tick: once a decision times
    // out, it's marked terminal ('escalated') here in the same pass that creates the replacement —
    // it simply stops being 'pending' and stops matching the scheduler's own candidate query.

    @Transactional
    public void checkAndEscalateIfTimedOut(WorkflowApprovalDecisionEntity decision) {
        WorkflowItemEntity item = itemRepo.findById(decision.getWorkflowItemId()).orElse(null);
        if (item == null || !"approval".equals(item.getType())) return;

        List<Map<String, Object>> levels = levelsOf(item);
        if (decision.getLevelOrder() >= levels.size()) return;
        Map<String, Object> level = levels.get(decision.getLevelOrder());

        Object timeoutObj = level.get("timeoutHours");
        if (timeoutObj == null) return; // no timeout configured for this level — never auto-escalates
        double timeoutHours = ((Number) timeoutObj).doubleValue();
        LocalDateTime deadline = decision.getCreatedAt().plusSeconds(Math.round(timeoutHours * 3600));
        if (LocalDateTime.now().isBefore(deadline)) return; // not due yet

        Object escalateToObj = level.get("escalateToUserId");
        if (escalateToObj == null) {
            // Cheap to repeat (a log line, no DB write) — deliberately NOT marked terminal here, so
            // the original approver can still act on it whenever they get to it; only a configured
            // escalation target makes this decision terminal (see the fix for a near-identical
            // "silent perpetual churn" bug found in the Dashboard AI-scoring feature — that one
            // wrote to the DB every tick and busted a downstream cache; this one only logs).
            log.warn("[ApprovalEscalation] Item {} level {} timed out with no escalateToUserId configured — left pending, no auto-escalation possible",
                    item.getId(), decision.getLevelOrder());
            return;
        }

        decision.setDecision("escalated");
        decision.setDecidedAt(LocalDateTime.now());
        decisionRepo.save(decision);
        log.info("[ApprovalEscalation] Item {} level {} timed out ({}h) — escalating to user {}",
                item.getId(), decision.getLevelOrder(), timeoutHours, escalateToObj);

        WorkflowApprovalDecisionEntity next = new WorkflowApprovalDecisionEntity();
        next.setWorkflowItemId(item.getId());
        next.setLevelOrder(decision.getLevelOrder());
        next.setApproverUserId(((Number) escalateToObj).intValue());
        decisionRepo.save(next);
        notifyApprovers(item, next);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> levelsOf(WorkflowItemEntity item) {
        Map<String, Object> typeConfig = item.getTypeConfig();
        if (typeConfig == null) return List.of();
        Object levelsObj = typeConfig.get("levels");
        return levelsObj instanceof List ? (List<Map<String, Object>>) levelsObj : List.of();
    }

    private void notifyApprovers(WorkflowItemEntity item, WorkflowApprovalDecisionEntity decision) {
        Long ticketId = item.getTicketId();
        Long itemId = item.getId();
        Long decisionId = decision.getId();

        new Thread(() -> {
            try {
                adminInboxService.createIfAbsent(ticketId, "wf_appr_" + itemId + "_" + decision.getLevelOrder(),
                        "APPROVAL_REQUIRED", "Approval required: " + item.getTitle() + " [TT-" + ticketId + "]");

                TicketEntity ticket = ticketRepo.findById(ticketId).orElse(null);
                WorkflowItemEntity freshItem = itemRepo.findById(itemId).orElse(null);
                if (ticket == null || freshItem == null) return;

                List<Long> recipientIds = new java.util.ArrayList<>();
                if (decision.getApproverUserId() != null) {
                    recipientIds.add(decision.getApproverUserId().longValue());
                }
                if (decision.getApproverGroupId() != null) {
                    groupMemberRepo.findByGroupId(decision.getApproverGroupId().longValue()).forEach(m -> {
                        if (!recipientIds.contains(m.getUserId())) recipientIds.add(m.getUserId());
                    });
                }
                if (recipientIds.isEmpty()) return;

                // One token per decision/level, shared across every recipient of that level (a group
                // approval's members all get the same link — whoever clicks first wins, the token is
                // single-use). In-app inbox notification still goes to everyone via the generic path.
                WorkflowApprovalTokenEntity tokenEntity = new WorkflowApprovalTokenEntity();
                tokenEntity.setWorkflowApprovalDecisionId(decisionId);
                tokenEntity.setToken(UUID.randomUUID().toString());
                tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(TOKEN_VALID_DAYS));
                tokenEntity = tokenRepo.save(tokenEntity);

                for (Long recipientId : recipientIds) {
                    notificationService.sendWorkflowItemNotification(freshItem, ticket, recipientId);
                }
                sendApprovalEmail(ticket, freshItem, recipientIds, tokenEntity.getToken());
            } catch (Exception e) {
                log.error("[Approval] Notification error for item {}: {}", itemId, e.getMessage(), e);
            }
        }).start();
    }

    private void sendApprovalEmail(TicketEntity ticket, WorkflowItemEntity item, List<Long> recipientIds, String token) {
        Optional<NotificationTemplateEntity> tmplOpt = templateRepo.findByNotificationType("workflow_approval_request");
        if (tmplOpt.isEmpty() || !tmplOpt.get().isEnabled()) {
            log.warn("[Approval] SKIPPED email — template 'workflow_approval_request' missing or disabled");
            return;
        }
        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[Approval] SKIPPED email — no default sender mailbox configured");
            return;
        }
        UserEntity requester = ticket.getRequestUserId() != null
                ? userRepo.findById(ticket.getRequestUserId().longValue()).orElse(null) : null;
        String requesterName = requester != null
                ? (requester.getDisplayName() != null ? requester.getDisplayName() : requester.getUsername())
                : "Unknown";

        String approveUrl = frontendUrl + "?approval=" + token + "&action=approve";
        String rejectUrl = frontendUrl + "?approval=" + token + "&action=reject";
        String ticketUrl = frontendUrl + "?ticket=" + ticket.getId();

        NotificationTemplateEntity tmpl = tmplOpt.get();
        String subject = resolvePlaceholders(tmpl.getSubjectTemplate(), ticket, item, requesterName, approveUrl, rejectUrl, ticketUrl);
        String body = resolvePlaceholders(tmpl.getBodyTemplate(), ticket, item, requesterName, approveUrl, rejectUrl, ticketUrl);

        for (Long recipientId : recipientIds) {
            UserEntity recipient = userRepo.findById(recipientId).orElse(null);
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                log.warn("[Approval] SKIPPED email — recipient user {} has no email address configured", recipientId);
                continue;
            }
            emailSenderService.sendReply(mailboxOpt.get(), recipient.getEmail(), subject, body, ticket.getId());
        }
    }

    private String resolvePlaceholders(String template, TicketEntity ticket, WorkflowItemEntity item,
                                        String requesterName, String approveUrl, String rejectUrl, String ticketUrl) {
        return template
                .replace("{{ticket_id}}", String.valueOf(ticket.getId()))
                .replace("{{ticket_title}}", ticket.getTitle() != null ? ticket.getTitle() : "")
                .replace("{{item_title}}", item.getTitle() != null ? item.getTitle() : "")
                .replace("{{requester_name}}", requesterName)
                .replace("{{approve_url}}", approveUrl)
                .replace("{{reject_url}}", rejectUrl)
                .replace("{{ticket_url}}", ticketUrl);
    }
}
