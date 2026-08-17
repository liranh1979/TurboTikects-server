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
import java.time.format.DateTimeFormatter;
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
    private final FieldDefinitionsRepository fieldDefRepo;
    private final HoursOfOperationRepository hooRepo;
    private final TimerCalculationService timerCalc;
    private final TicketRelationshipRepository relationshipRepo;

    public TicketService(TicketRepository ticketRepo,
                         TicketLabelAssignmentRepository labelAssignmentRepo,
                         TicketLabelRepository labelRepo,
                         TicketUpdateQueueRepository queueRepo,
                         TicketActivityLogRepository activityRepo,
                         TicketPresenceRepository presenceRepo,
                         UserRepository userRepo,
                         TicketSseService sseService,
                         FieldDefinitionsRepository fieldDefRepo,
                         HoursOfOperationRepository hooRepo,
                         TimerCalculationService timerCalc,
                         TicketRelationshipRepository relationshipRepo) {
        this.ticketRepo = ticketRepo;
        this.labelAssignmentRepo = labelAssignmentRepo;
        this.labelRepo = labelRepo;
        this.queueRepo = queueRepo;
        this.activityRepo = activityRepo;
        this.presenceRepo = presenceRepo;
        this.userRepo = userRepo;
        this.sseService = sseService;
        this.fieldDefRepo = fieldDefRepo;
        this.hooRepo = hooRepo;
        this.timerCalc = timerCalc;
        this.relationshipRepo = relationshipRepo;
    }

    // ── LIST / SEARCH ─────────────────────────────────────────────────────────

    public List<TicketListItemDto> getPage(Long cursor, int size, String search,
                                           String status, String priority, Long templateId,
                                           List<Long> labelIds, Integer responsibleUserId,
                                           Integer filterUserId, Integer companyId) {
        List<TicketEntity> tickets;
        if (search != null && !search.isBlank()) {
            tickets = filterUserId != null
                    ? ticketRepo.searchPageByUser(search + "*", cursor, size, filterUserId, companyId)
                    : ticketRepo.searchPage(search + "*", cursor, size, companyId);
        } else {
            tickets = ticketRepo.findPage(cursor, size, status, priority, templateId, responsibleUserId, filterUserId, companyId);
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
            dto.setPriority(t.getPriority());
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
            dto.setTicketData(t.getTicketData());
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

    public TicketDetailDto getById(Long id, Integer callerId, boolean isManager,
                                    boolean isSuperAdmin, Integer callerCompanyId) {
        TicketEntity ticket = ticketRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean isRequester = callerId != null && callerId.equals(ticket.getRequestUserId());

        if (isSuperAdmin || isRequester) {
            return toDetailDto(ticket);
        }

        if (ticket.getCompanyId() != null) {
            // Company-scoped ticket: only managers belonging to the same company may view
            if (isManager && callerCompanyId != null && callerCompanyId.equals(ticket.getCompanyId())) {
                return toDetailDto(ticket);
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (isManager) {
            return toDetailDto(ticket);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public TicketDetailDto create(CreateTicketRequestDto dto, Integer actorId) {
        return createInternal(dto, actorId, true, "manual");
    }

    @Transactional
    public TicketDetailDto create(CreateTicketRequestDto dto, Integer actorId, String source) {
        return createInternal(dto, actorId, true, source);
    }

    // ── CLONE ─────────────────────────────────────────────────────────────────

    @Transactional
    public TicketDetailDto clone(Long sourceId, String overrideTitle, Integer actorId) {
        TicketEntity source = ticketRepo.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        CreateTicketRequestDto dto = new CreateTicketRequestDto();
        dto.setTitle(overrideTitle != null && !overrideTitle.isBlank() ? overrideTitle : source.getTitle());
        dto.setDescription(source.getDescription());
        dto.setStatus("new");
        dto.setPriority(source.getPriority());
        dto.setTemplateId(source.getTemplateId());
        dto.setTemplateVersionId(source.getTemplateVersionId());
        dto.setResponsibleUserId(source.getResponsibleUserId());
        dto.setResponsibleGroupId(source.getResponsibleGroupId());
        dto.setTicketData(source.getTicketData() != null ? new LinkedHashMap<>(source.getTicketData()) : null);
        dto.setLabelIds(labelAssignmentRepo.findByTicketId(sourceId).stream()
                .map(TicketLabelAssignmentEntity::getLabelId).collect(Collectors.toList()));

        TicketDetailDto created = createInternal(dto, actorId, false, "clone");
        workflowService.cloneWorkflowItems(sourceId, created.getId());
        writeActivityLog(created.getId(), actorId, "TICKET_CLONED", Map.of("sourceTicketId", sourceId));
        return created;
    }

    private TicketDetailDto createInternal(CreateTicketRequestDto dto, Integer actorId, boolean seedWorkflowFromTemplate, String source) {
        TicketEntity ticket = new TicketEntity();
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setSolution(dto.getSolution());
        ticket.setStatus(dto.getStatus() != null ? dto.getStatus() : "new");
        ticket.setPriority(dto.getPriority() != null ? dto.getPriority() : "medium");
        ticket.setTemplateId(dto.getTemplateId());
        ticket.setTemplateVersionId(dto.getTemplateVersionId());
        Integer reqUserId = dto.getRequestUserId() != null ? dto.getRequestUserId() : actorId;
        ticket.setRequestUserId(reqUserId);
        // Inherit company from the request user
        Integer reqUserCompany = userRepo.findById(reqUserId.longValue())
                .map(u -> u.getCompanyId()).orElse(null);
        if (reqUserCompany != null) ticket.setCompanyId(reqUserCompany);
        ticket.setResponsibleUserId(dto.getResponsibleUserId());
        ticket.setResponsibleGroupId(dto.getResponsibleGroupId());
        ticket.setTicketData(processTimerFields(dto.getTicketData(), reqUserCompany));
        ticket.setSourceType(source);
        ticket.setVersion(1);
        ticket = ticketRepo.save(ticket);

        // Start SLA clocks against whatever policy matches this ticket's priority/template — a
        // no-op if no policy matches. Distinct from the generic timer field: never manually
        // entered, started exactly once here and never restamped by later unrelated edits.
        slaTimerService.startClocks(ticket);

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
        writeActivityLog(ticket.getId(), actorId, "TICKET_CREATED", "system",
                Map.of("title",      ticket.getTitle(),
                       "status",     ticket.getStatus(),
                       "priority",   ticket.getPriority(),
                       "templateId", ticket.getTemplateId() != null ? ticket.getTemplateId() : 0,
                       "source",     source));

        // Publish SSE
        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_UPDATED");
        event.setTicketId(ticket.getId());
        event.setUserId(actorId);
        event.setOperation("TICKET_CREATED");
        sseService.publish(event);

        // Seed workflow items from the template (no-op if it has no workflow field).
        // Skipped for clones — they copy the source ticket's real workflow items instead.
        if (seedWorkflowFromTemplate) {
            final Long finalTicketId = ticket.getId();
            final Long finalVersionId = ticket.getTemplateVersionId();
            final String finalStatus = ticket.getStatus();
            new Thread(() -> {
                try {
                    workflowService.seedWorkflowItems(finalTicketId, finalVersionId);
                    workflowService.cascadeTicketStatus(finalTicketId, finalStatus);
                } catch (Exception e) {
                    log.warn("[Workflow] Seed error for ticket {}: {}", finalTicketId, e.getMessage(), e);
                }
            }).start();
        }

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

        // Capture pre-update values for notifications and workflow cascade
        final String previousStatus              = ticket.getStatus();
        final Integer previousResponsibleUserId  = ticket.getResponsibleUserId();
        final Integer previousResponsibleGroupId = ticket.getResponsibleGroupId();

        if (dto.getRequestUserId() != null && !dto.getRequestUserId().equals(ticket.getRequestUserId())) {
            changes.put("requestUserId",
                    Map.of("from", nullSafe(ticket.getRequestUserId()), "to", dto.getRequestUserId()));
            ticket.setRequestUserId(dto.getRequestUserId());
            // Update company to track the new request user's company
            Integer newCompany = userRepo.findById(dto.getRequestUserId().longValue())
                    .map(u -> u.getCompanyId()).orElse(null);
            ticket.setCompanyId(newCompany);
        }
        if (dto.getTitle() != null && !dto.getTitle().equals(ticket.getTitle())) {
            changes.put("title", Map.of("from", ticket.getTitle(), "to", dto.getTitle()));
            ticket.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null && !dto.getDescription().equals(ticket.getDescription())) {
            changes.put("description", Map.of("from", nullSafe(ticket.getDescription()), "to", dto.getDescription()));
            ticket.setDescription(dto.getDescription());
        }
        if (dto.getSolution() != null && !dto.getSolution().equals(ticket.getSolution())) {
            changes.put("solution", Map.of("from", nullSafe(ticket.getSolution()), "to", dto.getSolution()));
            ticket.setSolution(dto.getSolution());
        }
        if (dto.getPriority() != null && !dto.getPriority().equals(ticket.getPriority())) {
            changes.put("priority", Map.of("from", ticket.getPriority(), "to", dto.getPriority()));
            ticket.setPriority(dto.getPriority());
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
            Integer companyId = ticket.getCompanyId();
            ticket.setTicketData(processTimerFields(dto.getTicketData(), companyId));
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
            writeActivityLog(ticket.getId(), actorId, "FIELD_UPDATE", "field_change", changes);
        }

        // First-response SLA: any edit made by someone other than the ticket's own requester
        // counts as the agent having engaged with it. No-op if already recorded or no SLA policy.
        if (!changes.isEmpty() && actorId != null && !actorId.equals(ticket.getRequestUserId())) {
            slaTimerService.recordFirstResponse(ticket.getId());
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

        // Cascade workflow item statuses when ticket status changed
        if (dto.getStatus() != null && !dto.getStatus().equals(previousStatus)) {
            final Long wfTicketId = ticket.getId();
            final String newStatus = ticket.getStatus();

            // SLA pause/resume/resolution — synchronous (fast, pure DB work, no external calls)
            // so it stays atomic with the status change itself rather than racing it.
            if (SlaTimerService.isWaitingStatus(previousStatus) && !SlaTimerService.isWaitingStatus(newStatus)) {
                slaTimerService.resume(wfTicketId, ticket.getCompanyId());
            } else if (!SlaTimerService.isWaitingStatus(previousStatus) && SlaTimerService.isWaitingStatus(newStatus)) {
                slaTimerService.pause(wfTicketId);
            }
            if (SlaTimerService.isResolvedStatus(newStatus) && !SlaTimerService.isResolvedStatus(previousStatus)) {
                slaTimerService.recordResolution(wfTicketId);
            } else if (!SlaTimerService.isResolvedStatus(newStatus) && SlaTimerService.isResolvedStatus(previousStatus)) {
                slaTimerService.clearResolution(wfTicketId);
            }

            new Thread(() -> {
                try {
                    workflowService.cascadeTicketStatus(wfTicketId, newStatus);
                } catch (Exception e) {
                    log.warn("[Workflow] Cascade error for ticket {}: {}", wfTicketId, e.getMessage(), e);
                }
            }).start();

            // CSAT survey: sent once, the first time a ticket reaches a resolved status
            final TicketEntity csatTicket = ticket;
            new Thread(() -> {
                try {
                    csatService.maybeSendSurvey(csatTicket, previousStatus, newStatus);
                } catch (Exception e) {
                    log.warn("[Csat] Survey dispatch error for ticket {}: {}", wfTicketId, e.getMessage(), e);
                }
            }).start();
        }

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

    // Package-private (not private) so TicketRelationshipService, in the same package,
    // can write activity-log entries without duplicating this helper.
    void writeActivityLog(Long ticketId, Integer actorId, String operation, Map<String, Object> changes) {
        writeActivityLog(ticketId, actorId, operation, "manual", changes);
    }

    void writeActivityLog(Long ticketId, Integer actorId, String operation,
                                   String activityType, Map<String, Object> changes) {
        TicketActivityLogEntity logEntry = new TicketActivityLogEntity();
        logEntry.setTicketId(ticketId);
        logEntry.setActorId(actorId);
        logEntry.setOperation(operation);
        logEntry.setActivityType(activityType);
        logEntry.setChanges(changes);
        activityRepo.save(logEntry);
    }

    // ── FILE ATTACHMENT ───────────────────────────────────────────────────────

    public void logAttachmentActivity(Long ticketId, Integer actorId, List<String> filenames) {
        if (!ticketRepo.existsById(ticketId)) return;
        writeActivityLog(ticketId, actorId != null ? actorId : 1, "FILE_ATTACHED", "file_attached",
                Map.of("filenames", filenames));
        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_UPDATED");
        event.setTicketId(ticketId);
        event.setUserId(actorId);
        event.setOperation("FILE_ATTACHED");
        sseService.publish(event);
    }

    // ── AI SOLUTION ───────────────────────────────────────────────────────────

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private CsatService csatService;

    @Autowired
    private TicketCsatRepository csatRepo;

    @Autowired
    private SlaTimerService slaTimerService;

    @Autowired
    private SlaPolicyRepository slaPolicyRepo;

    @Autowired
    private TicketSlaStateRepository ticketSlaStateRepo;

    public Map<String, Object> saveAiSolution(Long ticketId, String solution, Integer actorId) {
        if (!ticketRepo.existsById(ticketId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        writeActivityLog(ticketId, actorId != null ? actorId : 1, "AI_SOLUTION", "ai_solution",
                Map.of("body", solution));
        return Map.of("status", "ok");
    }

    /** Backs the ticket page's "Copy from AI Solution" button. There are two independent
     * write sites for ai_solution activity entries with different JSON keys — this manual
     * "Save as AI Solution" (Map.of("body", ...) above) and EmailProcessorService's automatic
     * inbound-email AI triage (Map.of("solution", ..., "confidence", ...)) — so both keys must
     * be checked, same as KbAiService.extractSolutionText() already does for the same reason. */
    public String getLatestAiSolutionText(Long ticketId) {
        org.springframework.data.domain.Page<TicketActivityLogEntity> page = activityRepo.findByTicketIdAndActivityTypeOrderByCreatedAtDesc(
                ticketId, "ai_solution", org.springframework.data.domain.PageRequest.of(0, 1));
        if (page.isEmpty()) return null;
        Map<String, Object> changes = page.getContent().get(0).getChanges();
        if (changes == null) return null;
        Object body = changes.get("body");
        if (body != null) return String.valueOf(body);
        Object solution = changes.get("solution");
        return solution != null ? String.valueOf(solution) : null;
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

    /**
     * For any timer field present in ticketData that has duration_value + duration_unit set
     * (and no target_datetime yet, or it changed), calculates and stamps started_at + target_datetime
     * using the company's Hours of Operation (or global if no company).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> processTimerFields(Map<String, Object> ticketData, Integer companyId) {
        if (ticketData == null) return null;

        // Load all timer field definitions
        List<FieldDefinitionsEntity> timerFields = fieldDefRepo
                .findByEntityTypeOrderByDisplayOrder("ticket")
                .stream()
                .filter(f -> "timer".equals(f.getFieldType()))
                .toList();

        if (timerFields.isEmpty()) return ticketData;

        // Load HoO schedule once
        List<HoursOfOperationEntity> schedule = companyId != null
                ? hooRepo.findByCompanyId(companyId)
                : hooRepo.findByCompanyIdIsNull();
        if (schedule.isEmpty()) schedule = hooRepo.findByCompanyIdIsNull();

        Map<String, Object> result = new LinkedHashMap<>(ticketData);

        for (FieldDefinitionsEntity field : timerFields) {
            Object raw = result.get(field.getFieldKey());
            if (!(raw instanceof Map)) continue;

            Map<String, Object> timerVal = new LinkedHashMap<>((Map<String, Object>) raw);
            Object durationValueRaw = timerVal.get("duration_value");
            Object durationUnitRaw  = timerVal.get("duration_unit");

            if (durationValueRaw == null || durationUnitRaw == null) continue;

            double durationValue;
            try { durationValue = Double.parseDouble(durationValueRaw.toString()); }
            catch (NumberFormatException e) { continue; }

            String durationUnit = durationUnitRaw.toString();
            LocalDateTime startedAt = LocalDateTime.now();

            LocalDateTime target = timerCalc.calculateTarget(startedAt, durationValue, durationUnit, schedule);

            timerVal.put("started_at", startedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            timerVal.put("target_datetime", target.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            result.put(field.getFieldKey(), timerVal);
        }

        return result;
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
        dto.setSolution(ticket.getSolution());
        dto.setStatus(ticket.getStatus());
        dto.setPriority(ticket.getPriority());
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
        csatRepo.findByTicketId(ticket.getId())
                .filter(c -> c.getRespondedAt() != null)
                .ifPresent(c -> {
                    dto.setCsatScore(c.getScore());
                    dto.setCsatComment(c.getComment());
                });

        List<TicketRelationshipEntity> relationships = relationshipRepo.findBySourceTicketIdOrderByCreatedAtDesc(ticket.getId());
        if (relationships.isEmpty()) {
            dto.setRelationships(Collections.emptyList());
        } else {
            Set<Long> otherTicketIds = relationships.stream()
                    .map(TicketRelationshipEntity::getTargetTicketId).collect(Collectors.toSet());
            Map<Long, TicketEntity> otherTicketsById = new HashMap<>();
            ticketRepo.findAllById(otherTicketIds).forEach(t -> otherTicketsById.put(t.getId(), t));
            dto.setRelationships(relationships.stream()
                    .map(r -> toRelationshipDto(r, otherTicketsById.get(r.getTargetTicketId())))
                    .collect(Collectors.toList()));
        }

        ticketSlaStateRepo.findByTicketId(ticket.getId())
                .ifPresent(state -> dto.setSlaState(toSlaStateDto(ticket, state)));

        return dto;
    }

    /**
     * Computes "% of SLA time used" for display without needing to separately track
     * paused-business-time-equivalents: the clock's target datetime is always kept correct as of
     * "now" by SlaTimerService (recomputed on every resume), so remaining business minutes is
     * simply the business-hours-aware walk from the reference point to that target, and elapsed %
     * falls out of remaining vs. the policy's fixed total — no need to reconstruct history.
     * While paused, the reference point is frozen at pausedAt instead of now, so the displayed
     * percentage doesn't silently creep forward from real time passing during the pause.
     */
    private SlaStateDto toSlaStateDto(TicketEntity ticket, TicketSlaStateEntity state) {
        SlaStateDto dto = new SlaStateDto();
        dto.setSlaPolicyId(state.getSlaPolicyId());
        dto.setFirstResponseTargetAt(state.getFirstResponseTargetAt());
        dto.setFirstResponseAt(state.getFirstResponseAt());
        dto.setFirstResponseBreached(state.isFirstResponseBreached());
        dto.setResolutionTargetAt(state.getResolutionTargetAt());
        dto.setResolutionAt(state.getResolutionAt());
        dto.setResolutionBreached(state.isResolutionBreached());
        dto.setPausedAt(state.getPausedAt());
        dto.setAiBreachRiskScore(state.getAiBreachRiskScore());
        dto.setAiRiskReason(state.getAiRiskReason());

        SlaPolicyEntity policy = state.getSlaPolicyId() != null
                ? slaPolicyRepo.findById(state.getSlaPolicyId()).orElse(null) : null;
        if (policy == null) return dto;
        dto.setBusinessHours(policy.isBusinessHours());

        List<HoursOfOperationEntity> schedule = policy.isBusinessHours()
                ? (ticket.getCompanyId() != null ? hooRepo.findByCompanyId(ticket.getCompanyId()) : hooRepo.findByCompanyIdIsNull())
                : Collections.emptyList();
        if (policy.isBusinessHours() && schedule.isEmpty()) schedule = hooRepo.findByCompanyIdIsNull();

        LocalDateTime referenceTime = state.getPausedAt() != null ? state.getPausedAt() : LocalDateTime.now();

        if (state.getFirstResponseAt() == null) {
            dto.setFirstResponsePercentUsed(
                    percentUsed(referenceTime, state.getFirstResponseTargetAt(), policy.getFirstResponseMinutes(), schedule));
        }
        if (state.getResolutionAt() == null) {
            dto.setResolutionPercentUsed(
                    percentUsed(referenceTime, state.getResolutionTargetAt(), policy.getResolutionMinutes(), schedule));
        }
        return dto;
    }

    private Double percentUsed(LocalDateTime referenceTime, LocalDateTime targetAt, int totalMinutes,
                                List<HoursOfOperationEntity> schedule) {
        if (targetAt == null || totalMinutes <= 0) return null;
        double remainingMinutes = referenceTime.isBefore(targetAt)
                ? timerCalc.calculateElapsedBusinessMinutes(referenceTime, targetAt, schedule) : 0;
        double pct = 100.0 * (1 - remainingMinutes / totalMinutes);
        return Math.min(100.0, Math.max(0.0, pct));
    }

    private TicketRelationshipDto toRelationshipDto(TicketRelationshipEntity r, TicketEntity other) {
        TicketRelationshipDto dto = new TicketRelationshipDto();
        dto.setId(r.getId());
        dto.setRelationshipType(r.getRelationshipType());
        dto.setOtherTicketId(r.getTargetTicketId());
        dto.setOtherTicketTitle(other != null ? other.getTitle() : null);
        dto.setOtherTicketStatus(other != null ? other.getStatus() : null);
        dto.setCreatedAt(r.getCreatedAt());
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
