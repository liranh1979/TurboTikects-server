package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.entitys.AccelerationRuleEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.repositorys.AccelerationRuleRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketActivityLogRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class AccelerationSchedulerService {

    private final AccelerationRuleRepository ruleRepo;
    private final TicketRepository ticketRepo;
    private final TicketActivityLogRepository activityLogRepo;
    private final AccelerationGroovyEngine groovyEngine;
    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;

    private volatile LocalDateTime lastRunAt = null;

    public AccelerationSchedulerService(AccelerationRuleRepository ruleRepo,
                                         TicketRepository ticketRepo,
                                         TicketActivityLogRepository activityLogRepo,
                                         AccelerationGroovyEngine groovyEngine,
                                         SystemSettingsService systemSettingsService,
                                         ObjectMapper objectMapper) {
        this.ruleRepo = ruleRepo;
        this.ticketRepo = ticketRepo;
        this.activityLogRepo = activityLogRepo;
        this.groovyEngine = groovyEngine;
        this.systemSettingsService = systemSettingsService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 60_000)
    public void poll() {
        try {
            int intervalMinutes = systemSettingsService.getAccelerationCronInterval();
            if (intervalMinutes <= 0) return;
            if (lastRunAt != null &&
                    Duration.between(lastRunAt, LocalDateTime.now()).toMinutes() < intervalMinutes) {
                return;
            }
            lastRunAt = LocalDateTime.now();
            runAllRules();
        } catch (Exception e) {
            log.error("Acceleration scheduler poll failed", e);
        }
    }

    public void runAllRules() {
        List<AccelerationRuleEntity> rules = ruleRepo.findByIsActiveTrueOrderByExecutionOrderAsc();
        log.info("Acceleration run: {} active rules", rules.size());
        for (AccelerationRuleEntity rule : rules) {
            processRule(rule);
        }
    }

    private void processRule(AccelerationRuleEntity rule) {
        if (rule.getGeneratedScript() == null || rule.getGeneratedScript().isBlank()) return;

        Specification<TicketEntity> spec = AccelerationSpecificationBuilder.fromConditionsJson(
                rule.getTriggerConditions(), objectMapper);

        int page = 0;
        Page<TicketEntity> batch;
        do {
            batch = ticketRepo.findAll(spec, PageRequest.of(page++, 100));
            for (TicketEntity ticket : batch.getContent()) {
                Integer oldAcceleration = ticket.getAcceleration();
                String oldStatus = ticket.getStatus();
                try {
                    AccelerationGroovyEngine.ExecutionResult result =
                            groovyEngine.execute(rule.getId(), ticket);
                    if (result.error() != null) {
                        rule.setLastError("Ticket " + ticket.getId() + ": " + result.error());
                        ruleRepo.save(rule);
                        continue;
                    }
                    if (result.mutated()) {
                        ticketRepo.save(ticket);
                        writeLog(ticket, rule, oldAcceleration, oldStatus);
                        if (rule.getLastError() != null) {
                            rule.setLastError(null);
                            ruleRepo.save(rule);
                        }
                    }
                } catch (Exception e) {
                    log.error("Rule {} failed on ticket {}", rule.getId(), ticket.getId(), e);
                    rule.setLastError("Ticket " + ticket.getId() + ": " + e.getMessage());
                    ruleRepo.save(rule);
                }
            }
        } while (batch.hasNext());
    }

    private void writeLog(TicketEntity ticket, AccelerationRuleEntity rule,
                          Integer oldAcceleration, String oldStatus) {
        Map<String, Object> changes = new HashMap<>();
        if (!Objects.equals(ticket.getAcceleration(), oldAcceleration)) {
            changes.put("acceleration", Map.of("from",
                    oldAcceleration != null ? oldAcceleration : 0,
                    "to", ticket.getAcceleration() != null ? ticket.getAcceleration() : 0));
        }
        if (!Objects.equals(ticket.getStatus(), oldStatus)) {
            changes.put("status", Map.of("from", oldStatus, "to", ticket.getStatus()));
        }
        if (changes.isEmpty()) return;

        TicketActivityLogEntity entry = new TicketActivityLogEntity();
        entry.setTicketId(ticket.getId());
        entry.setActorId(0); // 0 = system sentinel (no FK constraint in DB)
        entry.setOperation("ACCELERATION_APPLIED");
        entry.setActivityType("system");
        entry.setChanges(changes);
        entry.setMetadata(Map.of("ruleId", rule.getId(), "ruleName", rule.getName()));
        activityLogRepo.save(entry);
    }

}
