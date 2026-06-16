package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Every hour: scans open tickets for timer fields whose target_datetime is within
 * the configured alert threshold and creates in-app admin notifications.
 */
@Slf4j
@Component
public class TimerAlertScheduler {

    private final TicketRepository ticketRepo;
    private final FieldDefinitionsRepository fieldDefRepo;
    private final AdminInboxService adminInboxService;

    public TimerAlertScheduler(TicketRepository ticketRepo,
                                FieldDefinitionsRepository fieldDefRepo,
                                AdminInboxService adminInboxService) {
        this.ticketRepo = ticketRepo;
        this.fieldDefRepo = fieldDefRepo;
        this.adminInboxService = adminInboxService;
    }

    @Scheduled(fixedDelay = 3_600_000) // every hour
    public void checkTimers() {
        try {
            // Load all timer field definitions (entity_type = "ticket")
            List<FieldDefinitionsEntity> timerFields = fieldDefRepo
                    .findByEntityTypeOrderByDisplayOrder("ticket")
                    .stream()
                    .filter(f -> "timer".equals(f.getFieldType()))
                    .toList();

            if (timerFields.isEmpty()) return;

            // Only scan non-closed tickets
            List<TicketEntity> openTickets = ticketRepo.findByStatusNotIn(List.of("closed", "resolved"));

            LocalDateTime now = LocalDateTime.now();

            for (TicketEntity ticket : openTickets) {
                Map<String, Object> data = ticket.getTicketData();
                if (data == null) continue;

                for (FieldDefinitionsEntity field : timerFields) {
                    Object rawValue = data.get(field.getFieldKey());
                    if (!(rawValue instanceof Map)) continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> timerValue = (Map<String, Object>) rawValue;
                    Object rawTarget = timerValue.get("target_datetime");
                    if (rawTarget == null) continue;

                    LocalDateTime target;
                    try {
                        target = LocalDateTime.parse(rawTarget.toString());
                    } catch (Exception e) {
                        continue;
                    }

                    // Determine alert threshold from field config (default 20%)
                    double thresholdPercent = 20.0;
                    if (field.getFieldConfig() != null) {
                        Object rawThreshold = field.getFieldConfig().get("alert_threshold_percent");
                        if (rawThreshold instanceof Number) {
                            thresholdPercent = ((Number) rawThreshold).doubleValue();
                        }
                    }

                    // Calculate total duration and elapsed duration
                    Object rawStarted = timerValue.get("started_at");
                    if (rawStarted == null) continue;
                    LocalDateTime startedAt;
                    try {
                        startedAt = LocalDateTime.parse(rawStarted.toString());
                    } catch (Exception e) {
                        continue;
                    }

                    long totalSeconds   = java.time.Duration.between(startedAt, target).getSeconds();
                    long elapsedSeconds = java.time.Duration.between(startedAt, now).getSeconds();

                    if (totalSeconds <= 0) continue;

                    double progressPercent  = (elapsedSeconds * 100.0) / totalSeconds;
                    double remainingPercent = 100.0 - progressPercent;

                    // Fire when remaining % drops below threshold OR ticket is overdue
                    if (remainingPercent <= thresholdPercent || now.isAfter(target)) {
                        String message = String.format(
                                "Ticket TT-%d \"%s\" — timer field \"%s\" is %s.",
                                ticket.getId(), ticket.getTitle(), field.getFieldKey(),
                                now.isAfter(target) ? "overdue" : "expiring soon"
                        );
                        adminInboxService.createIfAbsent(ticket.getId(), field.getFieldKey(),
                                "TIMER_ALERT", message);
                    }
                }
            }
        } catch (Exception e) {
            log.error("TimerAlertScheduler error: {}", e.getMessage(), e);
        }
    }
}
