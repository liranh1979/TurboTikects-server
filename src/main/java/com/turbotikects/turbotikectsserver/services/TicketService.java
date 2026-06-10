package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TicketService {

    private final TicketRepository ticketRepo;
    private final TicketLabelAssignmentRepository labelAssignmentRepo;
    private final TicketLabelRepository labelRepo;
    private final TicketUpdateQueueRepository queueRepo;
    private final TicketActivityLogRepository activityRepo;
    private final TicketPresenceRepository presenceRepo;
    private final UserRepository userRepo;
    private final TicketSseService sseService;

    public TicketService(TicketRepository ticketRepo,
                         TicketLabelAssignmentRepository labelAssignmentRepo,
                         TicketLabelRepository labelRepo,
                         TicketUpdateQueueRepository queueRepo,
                         TicketActivityLogRepository activityRepo,
                         TicketPresenceRepository presenceRepo,
                         UserRepository userRepo,
                         TicketSseService sseService) {
        this.ticketRepo = ticketRepo;
        this.labelAssignmentRepo = labelAssignmentRepo;
        this.labelRepo = labelRepo;
        this.queueRepo = queueRepo;
        this.activityRepo = activityRepo;
        this.presenceRepo = presenceRepo;
        this.userRepo = userRepo;
        this.sseService = sseService;
    }

    // ── LIST / SEARCH ─────────────────────────────────────────────────────────

    public List<TicketListItemDto> getPage(Long cursor, int size, String search,
                                           String status, Long templateId,
                                           List<Long> labelIds, Integer responsibleUserId,
                                           Integer filterUserId) {
        List<TicketEntity> tickets;
        if (search != null && !search.isBlank()) {
            tickets = filterUserId != null
                    ? ticketRepo.searchPageByUser(search + "*", cursor, size, filterUserId)
                    : ticketRepo.searchPage(search + "*", cursor, size);
        } else {
            tickets = ticketRepo.findPage(cursor, size, status, templateId, responsibleUserId, filterUserId);
        }

        if (tickets.isEmpty()) return Collections.emptyList();

        List<Long> ticketIds = tickets.stream().map(TicketEntity::getId).collect(Collectors.toList());

        // Batch-load label assignments
        List<TicketLabelAssignmentEntity> assignments = labelAssignmentRepo.findByTicketIdIn(ticketIds);
        Map<Long, List<Long>> labelIdsByTicket = new HashMap<>();
        for (TicketLabelAssignmentEntity a : assignments) {
            labelIdsByTicket.computeIfAbsent(a.getTicketId(), k -> new ArrayList<>()).add(a.getLabelId());
        }

        // Load label entities
        Set<Long> allLabelIds = assignments.stream().map(TicketLabelAssignmentEntity::getLabelId)
                .collect(Collectors.toSet());
        Map<Long, TicketLabelEntity> labelsById = new HashMap<>();
        if (!allLabelIds.isEmpty()) {
            labelRepo.findAllById(allLabelIds).forEach(l -> labelsById.put(l.getId(), l));
        }

        // Collect user IDs to load display names
        Set<Long> userIds = new HashSet<>();
        for (TicketEntity t : tickets) {
            if (t.getRequestUserId() != null) userIds.add(t.getRequestUserId().longValue());
            if (t.getResponsibleUserId() != null) userIds.add(t.getResponsibleUserId().longValue());
        }
        Map<Long, String> displayNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepo.findAllById(new ArrayList<>(userIds))
                    .forEach(u -> displayNames.put(u.getRed_id(),
                            u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        }

        // Map to DTOs
        List<TicketListItemDto> result = new ArrayList<>();
        for (TicketEntity t : tickets) {
            List<Long> tLabelIds = labelIdsByTicket.getOrDefault(t.getId(), Collections.emptyList());

            // Post-filter: ticket must have ALL requested labels
            if (labelIds != null && !labelIds.isEmpty()) {
                if (!tLabelIds.containsAll(labelIds)) continue;
            }

            List<TicketLabelDto> labelDtos = tLabelIds.stream()
                    .map(labelsById::get)
                    .filter(Objects::nonNull)
                    .map(this::toLabelDto)
                    .collect(Collectors.toList());

            TicketListItemDto dto = new TicketListItemDto();
            dto.setId(t.getId());
            dto.setTitle(t.getTitle());
            dto.setStatus(t.getStatus());
            dto.setTemplateId(t.getTemplateId());
            dto.setTemplateVersionId(t.getTemplateVersionId());
            dto.setRequestUserId(t.getRequestUserId());
            dto.setRequestUserDisplayName(t.getRequestUserId() != null
                    ? displayNames.get(t.getRequestUserId().longValue()) : null);
            dto.setResponsibleUserId(t.getResponsibleUserId());
            dto.setResponsibleUserDisplayName(t.getResponsibleUserId() != null
                    ? displayNames.get(t.getResponsibleUserId().longValue()) : null);
            dto.setResponsibleGroupId(t.getResponsibleGroupId());
            dto.setVersion(t.getVersion());
            dto.setCreatedAt(t.getCreatedAt());
            dto.setUpdatedAt(t.getUpdatedAt());
            dto.setLabels(labelDtos);
            result.add(dto);
        }
        return result;
    }

    // ── GET BY ID ─────────────────────────────────────────────────────────────

    public TicketDetailDto getById(Long id) {
        TicketEntity ticket = ticketRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDetailDto(ticket);
    }

    public TicketDetailDto getById(Long id, Integer callerId, boolean isManager) {
        TicketEntity ticket = ticketRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isManager && (callerId == null || !callerId.equals(ticket.getRequestUserId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return toDetailDto(ticket);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public TicketDetailDto create(CreateTicketRequestDto dto, Integer actorId) {
        TicketEntity ticket = new TicketEntity();
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setStatus(dto.getStatus() != null ? dto.getStatus() : "new");
        ticket.setTemplateId(dto.getTemplateId());
        ticket.setTemplateVersionId(dto.getTemplateVersionId());
        ticket.setRequestUserId(dto.getRequestUserId() != null ? dto.getRequestUserId() : actorId);
        ticket.setResponsibleUserId(dto.getResponsibleUserId());
        ticket.setResponsibleGroupId(dto.getResponsibleGroupId());
        ticket.setTicketData(dto.getTicketData());
        ticket.setVersion(1);
        ticket = ticketRepo.save(ticket);

        // Save label assignments
        if (dto.getLabelIds() != null && !dto.getLabelIds().isEmpty()) {
            for (Long labelId : dto.getLabelIds()) {
                TicketLabelAssignmentEntity assignment = new TicketLabelAssignmentEntity();
                assignment.setTicketId(ticket.getId());
                assignment.setLabelId(labelId);
                labelAssignmentRepo.save(assignment);
            }
        }

        // Activity log
        writeActivityLog(ticket.getId(), actorId, "TICKET_CREATED",
                Map.of("title", ticket.getTitle(),
                        "status", ticket.getStatus(),
                        "templateId", ticket.getTemplateId()));

        // Publish SSE
        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_UPDATED");
        event.setTicketId(ticket.getId());
        event.setUserId(actorId);
        event.setOperation("TICKET_CREATED");
        sseService.publish(event);

        // Notifications (async)
        final TicketEntity savedTicket = ticket;
        notificationService.onTicketCreated(savedTicket, actorId);

        return toDetailDto(ticket);
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Transactional
    public TicketDetailDto patch(Long id, PatchTicketRequestDto dto, Integer actorId) {
        TicketEntity ticket = ticketRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Optimistic lock check
        if (ticket.getVersion() != dto.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Version conflict");
        }

        Map<String, Object> changes = new LinkedHashMap<>();

        // Capture pre-update responsible values for notification comparison
        final Integer previousResponsibleUserId  = ticket.getResponsibleUserId();
        final Integer previousResponsibleGroupId = ticket.getResponsibleGroupId();

        if (dto.getTitle() != null && !dto.getTitle().equals(ticket.getTitle())) {
            changes.put("title", Map.of("from", ticket.getTitle(), "to", dto.getTitle()));
            ticket.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null && !dto.getDescription().equals(ticket.getDescription())) {
            changes.put("description", Map.of("from", nullSafe(ticket.getDescription()), "to", dto.getDescription()));
            ticket.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null && !dto.getStatus().equals(ticket.getStatus())) {
            changes.put("status", Map.of("from", ticket.getStatus(), "to", dto.getStatus()));
            ticket.setStatus(dto.getStatus());
        }
        if (dto.getResponsibleUserId() != null
                && !dto.getResponsibleUserId().equals(ticket.getResponsibleUserId())) {
            changes.put("responsibleUserId",
                    Map.of("from", nullSafe(ticket.getResponsibleUserId()), "to", dto.getResponsibleUserId()));
            ticket.setResponsibleUserId(dto.getResponsibleUserId());
        }
        if (dto.getResponsibleGroupId() != null
                && !dto.getResponsibleGroupId().equals(ticket.getResponsibleGroupId())) {
            changes.put("responsibleGroupId",
                    Map.of("from", nullSafe(ticket.getResponsibleGroupId()), "to", dto.getResponsibleGroupId()));
            ticket.setResponsibleGroupId(dto.getResponsibleGroupId());
        }
        if (dto.getTicketData() != null) {
            changes.put("ticketData", Map.of("updated", true));
            ticket.setTicketData(dto.getTicketData());
        }

        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticket = ticketRepo.save(ticket);

        // Replace label assignments if provided
        if (dto.getLabelIds() != null) {
            labelAssignmentRepo.deleteByTicketId(id);
            for (Long labelId : dto.getLabelIds()) {
                TicketLabelAssignmentEntity assignment = new TicketLabelAssignmentEntity();
                assignment.setTicketId(id);
                assignment.setLabelId(labelId);
                labelAssignmentRepo.save(assignment);
            }
            changes.put("labels", Map.of("updated", true));
        }

        // Activity log
        if (!changes.isEmpty()) {
            writeActivityLog(ticket.getId(), actorId, "FIELD_UPDATE", changes);
        }

        // Publish SSE
        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_UPDATED");
        event.setTicketId(ticket.getId());
        event.setUserId(actorId);
        event.setOperation("FIELD_UPDATE");
        event.setNewVersion(ticket.getVersion());
        event.setChangedFields(new ArrayList<>(changes.keySet()));
        sseService.publish(event);

        // Notifications (async, only when something actually changed)
        if (!changes.isEmpty()) {
            final TicketEntity savedTicket = ticket;
            notificationService.onTicketPatched(savedTicket, actorId,
                    previousResponsibleUserId, previousResponsibleGroupId);
        }

        return toDetailDto(ticket);
    }

    // ── BULK DELETE ───────────────────────────────────────────────────────────

    @Transactional
    public void bulkDelete(List<Long> ids) {
        ticketRepo.deleteByIdIn(ids);
    }

    // ── BULK STATUS ───────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Integer> bulkUpdateStatus(List<Long> ids, String status, Integer actorId) {
        for (Long ticketId : ids) {
            enqueue(ticketId, "BULK_STATUS", Map.of("status", status), actorId);
        }
        return Map.of("queued", ids.size());
    }

    // ── BULK RESPONSIBLE ─────────────────────────────────────────────────────

    @Transactional
    public Map<String, Integer> bulkUpdateResponsible(List<Long> ids,
                                                       Integer responsibleUserId,
                                                       Integer responsibleGroupId,
                                                       Integer actorId) {
        String operation = responsibleUserId != null ? "BULK_RESPONSIBLE_USER" : "BULK_RESPONSIBLE_GROUP";
        Map<String, Object> payload = new HashMap<>();
        if (responsibleUserId != null) payload.put("userId", responsibleUserId);
        if (responsibleGroupId != null) payload.put("groupId", responsibleGroupId);

        for (Long ticketId : ids) {
            enqueue(ticketId, operation, payload, actorId);
        }
        return Map.of("queued", ids.size());
    }

    // ── BULK LABEL ────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Integer> bulkAddLabel(List<Long> ids, Long labelId, Integer actorId) {
        for (Long ticketId : ids) {
            enqueue(ticketId, "BULK_ADD_LABEL", Map.of("labelId", labelId), actorId);
        }
        return Map.of("queued", ids.size());
    }

    @Transactional
    public Map<String, Integer> bulkRemoveLabel(List<Long> ids, Long labelId, Integer actorId) {
        for (Long ticketId : ids) {
            enqueue(ticketId, "BULK_REMOVE_LABEL", Map.of("labelId", labelId), actorId);
        }
        return Map.of("queued", ids.size());
    }

    // ── PRESENCE ──────────────────────────────────────────────────────────────

    @Transactional
    public void updatePresence(Long ticketId, Integer userId, String displayName) {
        TicketPresenceEntity presence = new TicketPresenceEntity();
        presence.setTicketId(ticketId);
        presence.setUserId(userId);
        presence.setLastSeen(LocalDateTime.now());
        presenceRepo.save(presence);

        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_EDITING");
        event.setTicketId(ticketId);
        event.setUserId(userId);
        event.setDisplayName(displayName);
        sseService.publish(event);
    }

    public void presenceTyping(Long ticketId, Integer userId, String displayName) {
        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_EDITING");
        event.setTicketId(ticketId);
        event.setUserId(userId);
        event.setDisplayName(displayName);
        sseService.publish(event);
    }

    @Transactional
    public void removePresence(Long ticketId, Integer userId) {
        presenceRepo.deleteByTicketIdAndUserId(ticketId, userId);

        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_PRESENCE_LEFT");
        event.setTicketId(ticketId);
        event.setUserId(userId);
        sseService.publish(event);
    }

    // ── ACTIVITY LOG ──────────────────────────────────────────────────────────

    public List<TicketActivityLogEntity> getActivityLog(Long ticketId) {
        return activityRepo.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    public org.springframework.data.domain.Page<com.turbotikects.turbotikectsserver.dto.TicketActivityLogDto> getActivityPaged(
            Long ticketId, int page, int size, String type) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<TicketActivityLogEntity> entityPage;
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            entityPage = activityRepo.findByTicketIdOrderByCreatedAtDesc(ticketId, pageable);
        } else {
            entityPage = activityRepo.findByTicketIdAndActivityTypeOrderByCreatedAtDesc(ticketId, type, pageable);
        }

        // Gather actor IDs for display names
        Set<Long> actorIds = entityPage.getContent().stream()
                .map(e -> (long) e.getActorId())
                .collect(Collectors.toSet());
        Map<Long, String> displayNames = new HashMap<>();
        if (!actorIds.isEmpty()) {
            userRepo.findAllById(new ArrayList<>(actorIds))
                    .forEach(u -> displayNames.put(u.getRed_id(),
                            u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        }

        return entityPage.map(e -> {
            com.turbotikects.turbotikectsserver.dto.TicketActivityLogDto dto =
                    new com.turbotikects.turbotikectsserver.dto.TicketActivityLogDto();
            dto.setId(e.getId());
            dto.setTicketId(e.getTicketId());
            dto.setActorId(e.getActorId());
            dto.setActorDisplayName(displayNames.get((long) e.getActorId()));
            dto.setOperation(e.getOperation());
            dto.setActivityType(e.getActivityType());
            dto.setChanges(e.getChanges());
            dto.setMetadata(e.getMetadata());
            dto.setCreatedAt(e.getCreatedAt());
            return dto;
        });
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void enqueue(Long ticketId, String operation, Map<String, Object> payload, Integer actorId) {
        TicketUpdateQueueEntity item = new TicketUpdateQueueEntity();
        item.setTicketId(ticketId);
        item.setOperation(operation);
        item.setPayload(payload);
        item.setActorId(actorId);
        item.setStatus("pending");
        queueRepo.save(item);
    }

    private void writeActivityLog(Long ticketId, Integer actorId, String operation, Map<String, Object> changes) {
        writeActivityLog(ticketId, actorId, operation, "manual", changes);
    }

    private void writeActivityLog(Long ticketId, Integer actorId, String operation,
                                   String activityType, Map<String, Object> changes) {
        TicketActivityLogEntity logEntry = new TicketActivityLogEntity();
        logEntry.setTicketId(ticketId);
        logEntry.setActorId(actorId);
        logEntry.setOperation(operation);
        logEntry.setActivityType(activityType);
        logEntry.setChanges(changes);
        activityRepo.save(logEntry);
    }

    // ── AI SOLUTION ───────────────────────────────────────────────────────────

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private NotificationService notificationService;

    public Map<String, Object> saveAiSolution(Long ticketId, String solution, Integer actorId) {
        if (!ticketRepo.existsById(ticketId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        writeActivityLog(ticketId, actorId != null ? actorId : 1, "AI_SOLUTION", "ai_solution",
                Map.of("body", solution));
        return Map.of("status", "ok");
    }

    public Map<String, Object> sendSolutionEmail(Long ticketId, Long activityId, Integer actorId) {
        TicketEntity ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        TicketActivityLogEntity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));

        if (ticket.getRequestUserId() == null) {
            return Map.of("success", false, "message", "Ticket has no request user");
        }

        UserEntity requestUser = userRepo.findById(ticket.getRequestUserId().longValue()).orElse(null);
        if (requestUser == null || requestUser.getEmail() == null || requestUser.getEmail().isBlank()) {
            return Map.of("success", false, "message", "Request user has no email address configured");
        }

        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            return Map.of("success", false, "message", "No default sender mailbox configured");
        }

        String solution = activity.getChanges() != null
                ? activity.getChanges().getOrDefault("body", "").toString() : "";
        String subject = "[TT-" + ticketId + "] " + ticket.getTitle();
        String htmlBody = buildSolutionEmailHtml(ticket, solution);

        emailSenderService.sendReply(mailboxOpt.get(), requestUser.getEmail(), subject, htmlBody, ticketId);

        writeActivityLog(ticketId, actorId != null ? actorId : 1, "EMAIL_SOLUTION_SENT", "email_outbound",
                Map.of("to", requestUser.getEmail(), "subject", subject, "body", htmlBody));

        return Map.of("success", true, "message", "Email sent to " + requestUser.getEmail());
    }

    private String buildSolutionEmailHtml(TicketEntity ticket, String solution) {
        String desc = ticket.getDescription() != null ? ticket.getDescription() : "<em>No description provided.</em>";
        return "<div style='font-family:Arial,sans-serif;max-width:600px;color:#1a202c'>"
             + "<div style='background:#eff6ff;border-left:4px solid #3b82f6;padding:12px 16px;margin-bottom:16px;border-radius:0 6px 6px 0'>"
             + "<strong>Ticket [TT-" + ticket.getId() + "]:</strong> " + escapeHtml(ticket.getTitle())
             + "</div>"
             + "<p style='font-weight:600;margin:0 0 8px'>Your request:</p>"
             + "<div style='padding:12px;background:#f8fafc;border-radius:6px;margin-bottom:16px'>" + desc + "</div>"
             + "<p style='font-weight:600;margin:0 0 8px'>AI-assisted solution:</p>"
             + "<div style='padding:12px;background:#f0fdf4;border-left:4px solid #059669;border-radius:0 6px 6px 0'>" + solution + "</div>"
             + "<hr style='margin:24px 0;border:none;border-top:1px solid #e2e8f0'/>"
             + "<p style='color:#94a3b8;font-size:12px;margin:0'>This message was sent via the TurboTickets helpdesk system.</p>"
             + "</div>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private TicketDetailDto toDetailDto(TicketEntity ticket) {
        List<TicketLabelAssignmentEntity> assignments = labelAssignmentRepo.findByTicketId(ticket.getId());
        List<Long> tLabelIds = assignments.stream()
                .map(TicketLabelAssignmentEntity::getLabelId)
                .collect(Collectors.toList());

        Map<Long, TicketLabelEntity> labelsById = new HashMap<>();
        if (!tLabelIds.isEmpty()) {
            labelRepo.findAllById(tLabelIds).forEach(l -> labelsById.put(l.getId(), l));
        }

        List<TicketLabelDto> labelDtos = tLabelIds.stream()
                .map(labelsById::get)
                .filter(Objects::nonNull)
                .map(this::toLabelDto)
                .collect(Collectors.toList());

        // Load user display names
        Map<Long, String> displayNames = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        if (ticket.getRequestUserId() != null) userIds.add(ticket.getRequestUserId().longValue());
        if (ticket.getResponsibleUserId() != null) userIds.add(ticket.getResponsibleUserId().longValue());
        if (!userIds.isEmpty()) {
            userRepo.findAllById(new ArrayList<>(userIds))
                    .forEach(u -> displayNames.put(u.getRed_id(),
                            u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        }

        TicketDetailDto dto = new TicketDetailDto();
        dto.setId(ticket.getId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setStatus(ticket.getStatus());
        dto.setTemplateId(ticket.getTemplateId());
        dto.setTemplateVersionId(ticket.getTemplateVersionId());
        dto.setRequestUserId(ticket.getRequestUserId());
        dto.setRequestUserDisplayName(ticket.getRequestUserId() != null
                ? displayNames.get(ticket.getRequestUserId().longValue()) : null);
        dto.setResponsibleUserId(ticket.getResponsibleUserId());
        dto.setResponsibleUserDisplayName(ticket.getResponsibleUserId() != null
                ? displayNames.get(ticket.getResponsibleUserId().longValue()) : null);
        dto.setResponsibleGroupId(ticket.getResponsibleGroupId());
        dto.setTicketData(ticket.getTicketData());
        dto.setVersion(ticket.getVersion());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());
        dto.setLabels(labelDtos);
        return dto;
    }

    private TicketLabelDto toLabelDto(TicketLabelEntity entity) {
        TicketLabelDto dto = new TicketLabelDto();
        dto.setId(entity.getId());
        dto.setLabelKey(entity.getLabelKey());
        dto.setColor(entity.getColor());
        return dto;
    }

    private Object nullSafe(Object value) {
        return value != null ? value : "";
    }
}
