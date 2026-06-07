package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.TicketSseEventDto;
import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketLabelAssignmentEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketUpdateQueueEntity;
import com.turbotikects.turbotikectsserver.repositorys.TicketActivityLogRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketLabelAssignmentRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketLabelRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketUpdateQueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TicketQueueProcessorService {

    private final TicketUpdateQueueRepository queueRepo;
    private final TicketRepository ticketRepo;
    private final TicketLabelAssignmentRepository labelAssignmentRepo;
    private final TicketLabelRepository labelRepo;
    private final TicketActivityLogRepository activityRepo;
    private final TicketSseService sseService;

    private final String instanceId;

    public TicketQueueProcessorService(TicketUpdateQueueRepository queueRepo,
                                       TicketRepository ticketRepo,
                                       TicketLabelAssignmentRepository labelAssignmentRepo,
                                       TicketLabelRepository labelRepo,
                                       TicketActivityLogRepository activityRepo,
                                       TicketSseService sseService) {
        this.queueRepo = queueRepo;
        this.ticketRepo = ticketRepo;
        this.labelAssignmentRepo = labelAssignmentRepo;
        this.labelRepo = labelRepo;
        this.activityRepo = activityRepo;
        this.sseService = sseService;

        String id;
        try {
            id = java.net.InetAddress.getLocalHost().getHostName() + ":" + System.currentTimeMillis();
        } catch (java.net.UnknownHostException e) {
            id = UUID.randomUUID().toString();
        }
        this.instanceId = id;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processQueue() {
        List<TicketUpdateQueueEntity> batch = queueRepo.claimBatch(25);
        if (batch.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (TicketUpdateQueueEntity item : batch) {
            item.setStatus("processing");
            item.setClaimedBy(instanceId);
            item.setClaimedAt(now);
            queueRepo.save(item);
        }

        for (TicketUpdateQueueEntity item : batch) {
            try {
                applyQueueItem(item);
                item.setStatus("done");
            } catch (Exception e) {
                log.error("Failed to process queue item {}: {}", item.getId(), e.getMessage());
                item.setStatus("failed");
            }
            queueRepo.save(item);
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void recoverStuck() {
        int recovered = queueRepo.resetStuck(LocalDateTime.now().minusSeconds(60));
        if (recovered > 0) log.info("Recovered {} stuck queue items", recovered);
    }

    private void applyQueueItem(TicketUpdateQueueEntity item) {
        Map<String, Object> payload = item.getPayload();
        Long ticketId = item.getTicketId();

        switch (item.getOperation()) {
            case "BULK_STATUS" -> {
                String status = (String) payload.get("status");
                ticketRepo.findById(ticketId).ifPresent(t -> {
                    t.setStatus(status);
                    t.setUpdatedAt(LocalDateTime.now());
                    ticketRepo.save(t);
                });
                writeActivityLog(ticketId, item.getActorId(), "STATUS_CHANGE",
                        Map.of("status", Map.of("to", status)));
            }
            case "BULK_RESPONSIBLE_USER" -> {
                Integer userId = payload.get("userId") != null
                        ? ((Number) payload.get("userId")).intValue() : null;
                ticketRepo.findById(ticketId).ifPresent(t -> {
                    t.setResponsibleUserId(userId);
                    t.setUpdatedAt(LocalDateTime.now());
                    ticketRepo.save(t);
                });
                writeActivityLog(ticketId, item.getActorId(), "RESPONSIBLE_CHANGE", payload);
            }
            case "BULK_RESPONSIBLE_GROUP" -> {
                Integer groupId = payload.get("groupId") != null
                        ? ((Number) payload.get("groupId")).intValue() : null;
                ticketRepo.findById(ticketId).ifPresent(t -> {
                    t.setResponsibleGroupId(groupId);
                    t.setUpdatedAt(LocalDateTime.now());
                    ticketRepo.save(t);
                });
                writeActivityLog(ticketId, item.getActorId(), "RESPONSIBLE_CHANGE", payload);
            }
            case "BULK_ADD_LABEL" -> {
                Long labelId = ((Number) payload.get("labelId")).longValue();
                TicketLabelAssignmentEntity assignment = new TicketLabelAssignmentEntity();
                assignment.setTicketId(ticketId);
                assignment.setLabelId(labelId);
                try {
                    labelAssignmentRepo.save(assignment);
                } catch (Exception ignored) {
                    // Ignore duplicate key violations — label already assigned
                }
                writeActivityLog(ticketId, item.getActorId(), "LABEL_ADD", payload);
            }
            case "BULK_REMOVE_LABEL" -> {
                Long labelId = ((Number) payload.get("labelId")).longValue();
                labelAssignmentRepo.deleteByTicketIdsAndLabelId(List.of(ticketId), labelId);
                writeActivityLog(ticketId, item.getActorId(), "LABEL_REMOVE", payload);
            }
            default -> log.warn("Unknown queue operation: {}", item.getOperation());
        }

        TicketSseEventDto event = new TicketSseEventDto();
        event.setType("TICKET_UPDATED");
        event.setTicketId(ticketId);
        event.setUserId(item.getActorId());
        event.setOperation(item.getOperation());
        sseService.publish(event);
    }

    private void writeActivityLog(Long ticketId, Integer actorId, String operation, Map<String, Object> changes) {
        TicketActivityLogEntity logEntry = new TicketActivityLogEntity();
        logEntry.setTicketId(ticketId);
        logEntry.setActorId(actorId);
        logEntry.setOperation(operation);
        logEntry.setChanges(changes);
        activityRepo.save(logEntry);
    }
}
