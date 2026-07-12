package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TicketRelationshipService {

    private static final String MERGE_NOTIFY_TEMPLATE_TYPE = "ticket_merge_notify_requester";

    private static final Map<String, String> INVERSE = Map.of(
            "relates_to", "relates_to",
            "blocks", "blocked_by",
            "blocked_by", "blocks",
            "duplicates", "duplicated_by",
            "duplicated_by", "duplicates",
            "caused_by", "causes",
            "causes", "caused_by",
            "child_of", "parent_of",
            "parent_of", "child_of");

    private final TicketRepository ticketRepo;
    private final TicketRelationshipRepository relationshipRepo;
    private final AttachmentRepository attachmentRepo;
    private final TicketActivityLogRepository activityRepo;
    private final TicketSseService sseService;
    private final WorkflowService workflowService;
    private final EmailSenderService emailSenderService;
    private final NotificationTemplateRepository templateRepo;
    private final UserNotificationService userNotificationService;
    private final UserRepository userRepo;
    private final TicketService ticketService;

    public TicketRelationshipService(TicketRepository ticketRepo,
                                      TicketRelationshipRepository relationshipRepo,
                                      AttachmentRepository attachmentRepo,
                                      TicketActivityLogRepository activityRepo,
                                      TicketSseService sseService,
                                      WorkflowService workflowService,
                                      EmailSenderService emailSenderService,
                                      NotificationTemplateRepository templateRepo,
                                      UserNotificationService userNotificationService,
                                      UserRepository userRepo,
                                      TicketService ticketService) {
        this.ticketRepo = ticketRepo;
        this.relationshipRepo = relationshipRepo;
        this.attachmentRepo = attachmentRepo;
        this.activityRepo = activityRepo;
        this.sseService = sseService;
        this.workflowService = workflowService;
        this.emailSenderService = emailSenderService;
        this.templateRepo = templateRepo;
        this.userNotificationService = userNotificationService;
        this.userRepo = userRepo;
        this.ticketService = ticketService;
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    public List<TicketRelationshipDto> list(Long ticketId) {
        if (!ticketRepo.existsById(ticketId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        List<TicketRelationshipEntity> relationships = relationshipRepo.findBySourceTicketIdOrderByCreatedAtDesc(ticketId);
        if (relationships.isEmpty()) return Collections.emptyList();

        Set<Long> otherIds = relationships.stream()
                .map(TicketRelationshipEntity::getTargetTicketId).collect(Collectors.toSet());
        Map<Long, TicketEntity> otherById = new HashMap<>();
        ticketRepo.findAllById(otherIds).forEach(t -> otherById.put(t.getId(), t));

        return relationships.stream().map(r -> toDto(r, otherById.get(r.getTargetTicketId())))
                .collect(Collectors.toList());
    }

    // ── LINK ──────────────────────────────────────────────────────────────────

    @Transactional
    public TicketRelationshipDto link(Long ticketId, LinkTicketRequestDto req, Integer actorId) {
        Long targetId = req.getTargetTicketId();
        String type = req.getRelationshipType();

        if (targetId == null || type == null || !INVERSE.containsKey(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid relationship request");
        }
        if (targetId.equals(ticketId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ticket cannot be linked to itself");
        }
        TicketEntity source = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        TicketEntity target = ticketRepo.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target ticket not found"));

        if (relationshipRepo.existsBySourceTicketIdAndTargetTicketIdAndRelationshipType(ticketId, targetId, type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "These tickets are already linked this way");
        }

        String inverseType = INVERSE.get(type);

        TicketRelationshipEntity forward = new TicketRelationshipEntity();
        forward.setSourceTicketId(ticketId);
        forward.setTargetTicketId(targetId);
        forward.setRelationshipType(type);
        forward.setCreatedBy(actorId);
        forward = relationshipRepo.save(forward);

        TicketRelationshipEntity inverse = new TicketRelationshipEntity();
        inverse.setSourceTicketId(targetId);
        inverse.setTargetTicketId(ticketId);
        inverse.setRelationshipType(inverseType);
        inverse.setCreatedBy(actorId);
        relationshipRepo.save(inverse);

        ticketService.writeActivityLog(ticketId, actorId, "TICKET_LINKED",
                Map.of("otherTicketId", targetId, "relationshipType", type));
        ticketService.writeActivityLog(targetId, actorId, "TICKET_LINKED",
                Map.of("otherTicketId", ticketId, "relationshipType", inverseType));

        touchAndPublish(source, actorId, "TICKET_LINKED");
        touchAndPublish(target, actorId, "TICKET_LINKED");

        return toDto(forward, target);
    }

    // ── UNLINK ────────────────────────────────────────────────────────────────

    @Transactional
    public void unlink(Long ticketId, Long relationshipId, Integer actorId) {
        TicketRelationshipEntity row = relationshipRepo.findById(relationshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relationship not found"));
        if (!row.getSourceTicketId().equals(ticketId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Relationship does not belong to this ticket");
        }

        Long otherId = row.getTargetTicketId();
        String inverseType = INVERSE.get(row.getRelationshipType());

        relationshipRepo.delete(row);
        relationshipRepo.findBySourceTicketIdAndTargetTicketIdAndRelationshipType(otherId, ticketId, inverseType)
                .ifPresent(relationshipRepo::delete);

        ticketService.writeActivityLog(ticketId, actorId, "TICKET_UNLINKED", Map.of("otherTicketId", otherId));
        ticketService.writeActivityLog(otherId, actorId, "TICKET_UNLINKED", Map.of("otherTicketId", ticketId));

        TicketEntity source = ticketRepo.findById(ticketId).orElse(null);
        TicketEntity target = ticketRepo.findById(otherId).orElse(null);
        if (source != null) touchAndPublish(source, actorId, "TICKET_UNLINKED");
        if (target != null) touchAndPublish(target, actorId, "TICKET_UNLINKED");
    }

    // ── MERGE ─────────────────────────────────────────────────────────────────

    @Transactional
    public MergeResultDto merge(Long sourceId, MergeTicketRequestDto req, Integer actorId) {
        if (req.getTargetTicketId() == null || req.getTargetTicketId().equals(sourceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid merge target");
        }
        TicketEntity source = ticketRepo.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        TicketEntity target = ticketRepo.findById(req.getTargetTicketId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target ticket not found"));

        int attachmentsMoved = 0;
        if (req.isMoveAttachments()) {
            attachmentsMoved = attachmentRepo.reassignTicketAttachments(sourceId, target.getId());
        }
        int activityRowsCopied = activityRepo.copyActivityLogRows(sourceId, target.getId());

        String previousStatus = source.getStatus();
        source.setStatus("closed");
        source.setVersion(source.getVersion() + 1);
        source.setUpdatedAt(LocalDateTime.now());
        source = ticketRepo.save(source);

        final Long finalSourceId = source.getId();
        final String finalStatus = source.getStatus();
        new Thread(() -> {
            try {
                workflowService.cascadeTicketStatus(finalSourceId, finalStatus);
            } catch (Exception e) {
                log.warn("[Workflow] Cascade error for merged ticket {}: {}", finalSourceId, e.getMessage(), e);
            }
        }).start();

        ticketService.writeActivityLog(sourceId, actorId, "TICKET_MERGED",
                Map.of("mergedIntoTicketId", target.getId()));
        ticketService.writeActivityLog(target.getId(), actorId, "TICKET_MERGED_FROM",
                Map.of("mergedFromTicketId", sourceId, "attachmentsMoved", attachmentsMoved,
                        "activityRowsCopied", activityRowsCopied));

        if (req.isAddComment()) {
            ticketService.writeActivityLog(sourceId, actorId, "TICKET_MERGE_NOTE", "manual",
                    Map.of("otherTicketId", target.getId()));
            ticketService.writeActivityLog(target.getId(), actorId, "TICKET_MERGE_NOTE", "manual",
                    Map.of("otherTicketId", sourceId));
        }

        touchAndPublish(target, actorId, "TICKET_MERGED_FROM");
        publishEvent(sourceId, actorId, "TICKET_MERGED", source.getVersion());

        if (req.isNotifyRequester()) {
            final TicketEntity notifySource = source;
            final TicketEntity notifyTarget = target;
            new Thread(() -> {
                try {
                    notifyRequesterOfMerge(notifySource, notifyTarget);
                } catch (Exception e) {
                    log.warn("[Merge] Notify-requester error for ticket {}: {}", finalSourceId, e.getMessage(), e);
                }
            }).start();
        }

        log.info("[Merge] Ticket {} (was {}) merged into {} by actor {} ({} attachments, {} activity rows)",
                sourceId, previousStatus, target.getId(), actorId, attachmentsMoved, activityRowsCopied);

        MergeResultDto result = new MergeResultDto();
        result.setSourceTicket(ticketService.getById(sourceId));
        result.setTargetTicket(ticketService.getById(target.getId()));
        return result;
    }

    private void notifyRequesterOfMerge(TicketEntity source, TicketEntity target) {
        if (source.getRequestUserId() == null) return;
        UserEntity requester = userRepo.findById(source.getRequestUserId().longValue()).orElse(null);
        if (requester == null) {
            log.warn("[Merge] SKIPPED notify — requester {} not found for ticket {}",
                    source.getRequestUserId(), source.getId());
            return;
        }

        userNotificationService.create(requester.getRed_id(), target.getId(), "TICKET_MERGED",
                "Your ticket TT-" + source.getId() + " was merged into TT-" + target.getId(), null);

        Optional<NotificationTemplateEntity> tmplOpt = templateRepo.findByNotificationType(MERGE_NOTIFY_TEMPLATE_TYPE);
        if (tmplOpt.isEmpty() || !tmplOpt.get().isEnabled()) {
            log.warn("[Merge] SKIPPED email – template '{}' missing or disabled", MERGE_NOTIFY_TEMPLATE_TYPE);
            return;
        }
        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[Merge] SKIPPED email – no default sender mailbox configured");
            return;
        }
        if (requester.getEmail() == null || requester.getEmail().isBlank()) {
            log.warn("[Merge] SKIPPED email – requester {} has no email address configured", requester.getRed_id());
            return;
        }

        UserEntity responsible = target.getResponsibleUserId() != null
                ? userRepo.findById(target.getResponsibleUserId().longValue()).orElse(null) : null;
        String responsibleName = responsible != null
                ? (responsible.getDisplayName() != null ? responsible.getDisplayName() : responsible.getUsername())
                : "our team";
        String requesterName = requester.getDisplayName() != null ? requester.getDisplayName() : requester.getUsername();

        NotificationTemplateEntity tmpl = tmplOpt.get();
        String subject = resolvePlaceholders(tmpl.getSubjectTemplate(), source, target, requesterName, responsibleName);
        String body = resolvePlaceholders(tmpl.getBodyTemplate(), source, target, requesterName, responsibleName);

        emailSenderService.sendReply(mailboxOpt.get(), requester.getEmail(), subject, body, source.getId());
    }

    private String resolvePlaceholders(String template, TicketEntity source, TicketEntity target,
                                        String requesterName, String responsibleName) {
        return template
                .replace("{{ticket_id}}", String.valueOf(source.getId()))
                .replace("{{ticket_title}}", source.getTitle())
                .replace("{{merged_into_id}}", String.valueOf(target.getId()))
                .replace("{{requester_name}}", requesterName)
                .replace("{{responsible_name}}", responsibleName);
    }

    // ── PICKER ────────────────────────────────────────────────────────────────

    public List<TicketPickerItemDto> searchPicker(String q, Long excludeTicketId, int limit, Integer companyId) {
        String query = q == null ? "" : q.trim();
        Long idMatch = -1L;
        try {
            idMatch = Long.parseLong(query);
        } catch (NumberFormatException ignored) {
            // not a numeric query — fall back to title LIKE match only
        }
        List<TicketEntity> tickets = ticketRepo.searchForPicker(query, idMatch, excludeTicketId, companyId, limit);
        return tickets.stream().map(t -> {
            TicketPickerItemDto dto = new TicketPickerItemDto();
            dto.setId(t.getId());
            dto.setTitle(t.getTitle());
            dto.setStatus(t.getStatus());
            return dto;
        }).collect(Collectors.toList());
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private TicketRelationshipDto toDto(TicketRelationshipEntity r, TicketEntity other) {
        TicketRelationshipDto dto = new TicketRelationshipDto();
        dto.setId(r.getId());
        dto.setRelationshipType(r.getRelationshipType());
        dto.setOtherTicketId(r.getTargetTicketId());
        dto.setOtherTicketTitle(other != null ? other.getTitle() : null);
        dto.setOtherTicketStatus(other != null ? other.getStatus() : null);
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }

    private void touchAndPublish(TicketEntity ticket, Integer actorId, String operation) {
        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepo.save(ticket);
        publishEvent(ticket.getId(), actorId, operation, ticket.getVersion());
    }

    private void publishEvent(Long ticketId, Integer actorId, String operation, Integer newVersion) {
        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_UPDATED");
        event.setTicketId(ticketId);
        event.setUserId(actorId);
        event.setOperation(operation);
        if (newVersion != null) event.setNewVersion(newVersion);
        sseService.publish(event);
    }
}
