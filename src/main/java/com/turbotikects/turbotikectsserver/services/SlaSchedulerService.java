package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Ticks every 10 seconds (plus once on application startup, so a period of downtime doesn't leave
 * a silent gap) checking every open ticket's SLA state against its policy's escalation ladder, and
 * fires any step whose trigger has been crossed and hasn't already fired for that ticket. Cheap by
 * design — pure arithmetic against already-computed targets, no LLM calls (that's Phase 4's job,
 * added onto this same tick later). See plan decision #3 and #7.
 */
@Slf4j
@Service
public class SlaSchedulerService {

    private final TicketRepository ticketRepo;
    private final TicketSlaStateRepository stateRepo;
    private final SlaPolicyRepository policyRepo;
    private final SlaEscalationStepRepository stepRepo;
    private final TicketSlaEscalationFiredRepository firedRepo;
    private final SlaTimerService slaTimerService;
    private final SlaEscalationExecutor executor;
    private final AiTriageAgent aiTriageAgent;

    public SlaSchedulerService(TicketRepository ticketRepo, TicketSlaStateRepository stateRepo,
                                SlaPolicyRepository policyRepo, SlaEscalationStepRepository stepRepo,
                                TicketSlaEscalationFiredRepository firedRepo, SlaTimerService slaTimerService,
                                SlaEscalationExecutor executor, AiTriageAgent aiTriageAgent) {
        this.ticketRepo = ticketRepo;
        this.stateRepo = stateRepo;
        this.policyRepo = policyRepo;
        this.stepRepo = stepRepo;
        this.firedRepo = firedRepo;
        this.slaTimerService = slaTimerService;
        this.executor = executor;
        this.aiTriageAgent = aiTriageAgent;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void tick() {
        try {
            runEscalationCheck();
        } catch (Exception e) {
            log.error("[SLA] Scheduler tick failed", e);
        }
        try {
            aiTriageAgent.scoreStaleTickets();
        } catch (Exception e) {
            log.error("[SLA] AI triage tick failed", e);
        }
    }

    /**
     * Catch-up pass for any escalations that should have fired (and any AI scores that should have
     * been (re)computed) while the app was down. The `initialDelay` above already keeps the very
     * first `@Scheduled` tick from landing right on top of this at boot, but `runEscalationCheck` is
     * additionally `synchronized` as a hard guarantee — without it, two concurrent passes can both
     * read "not yet fired" for the same ticket+step before either writes the fired row, executing
     * (and notifying/webhooking) it twice. AI scoring has no such uniqueness constraint to race, so
     * it isn't part of that same lock — worst case an overlap re-scores the same ticket twice, which
     * just overwrites harmlessly.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[SLA] Running startup catch-up escalation check");
        try {
            runEscalationCheck();
        } catch (Exception e) {
            log.error("[SLA] Startup catch-up escalation check failed", e);
        }
        try {
            aiTriageAgent.scoreStaleTickets();
        } catch (Exception e) {
            log.error("[SLA] Startup AI triage check failed", e);
        }
    }

    public synchronized void runEscalationCheck() {
        List<TicketEntity> openTickets = ticketRepo.findByStatusNotIn(SlaTimerService.RESOLVED_STATUSES);
        for (TicketEntity ticket : openTickets) {
            try {
                checkTicket(ticket);
            } catch (Exception e) {
                log.error("[SLA] Escalation check failed for ticket {}", ticket.getId(), e);
            }
        }
    }

    private void checkTicket(TicketEntity ticket) {
        Optional<TicketSlaStateEntity> stateOpt = stateRepo.findByTicketId(ticket.getId());
        if (stateOpt.isEmpty()) return;
        TicketSlaStateEntity state = stateOpt.get();
        if (state.getSlaPolicyId() == null || state.getPausedAt() != null) return; // no policy, or clock frozen

        SlaPolicyEntity policy = policyRepo.findById(state.getSlaPolicyId()).orElse(null);
        if (policy == null) return;

        List<SlaEscalationStepEntity> steps = stepRepo.findBySlaPolicyIdOrderByStepOrderAsc(policy.getId());
        if (steps.isEmpty()) return;

        List<HoursOfOperationEntity> schedule = slaTimerService.resolveSchedule(ticket.getCompanyId(), policy.isBusinessHours());
        LocalDateTime now = LocalDateTime.now();

        for (SlaEscalationStepEntity step : steps) {
            if (firedRepo.existsByTicketIdAndSlaEscalationStepId(ticket.getId(), step.getId())) continue;

            TriggerEval eval = evaluateTrigger(state, policy, step, schedule, now);
            if (!eval.triggered) continue;

            executor.execute(ticket, step, eval.isBreach, eval.percentUsed);

            TicketSlaEscalationFiredEntity fired = new TicketSlaEscalationFiredEntity();
            fired.setTicketId(ticket.getId());
            fired.setSlaEscalationStepId(step.getId());
            firedRepo.save(fired);
        }
    }

    private record TriggerEval(boolean triggered, boolean isBreach, double percentUsed) {}

    /**
     * PERCENT_OF_RESPONSE evaluates the first-response clock; PERCENT_OF_RESOLUTION, AT_BREACH and
     * AFTER_BREACH_MINUTES all evaluate the resolution clock (the ladder's "final" deadline) — a
     * first-response breach on its own only ever warrants its dedicated PERCENT_OF_RESPONSE warning
     * step, not the breach-escalation steps, since an unaddressed first response naturally flows
     * into a resolution breach anyway.
     */
    private TriggerEval evaluateTrigger(TicketSlaStateEntity state, SlaPolicyEntity policy,
                                         SlaEscalationStepEntity step, List<HoursOfOperationEntity> schedule,
                                         LocalDateTime now) {
        BigDecimal triggerValue = step.getTriggerValue();
        switch (step.getTriggerType()) {
            case "PERCENT_OF_RESPONSE" -> {
                if (state.getFirstResponseAt() != null || state.getFirstResponseTargetAt() == null || triggerValue == null) {
                    return new TriggerEval(false, false, 0);
                }
                double pct = slaTimerService.percentUsed(now, state.getFirstResponseTargetAt(), policy.getFirstResponseMinutes(), schedule);
                return new TriggerEval(pct >= triggerValue.doubleValue(), now.isAfter(state.getFirstResponseTargetAt()), pct);
            }
            case "PERCENT_OF_RESOLUTION" -> {
                if (state.getResolutionAt() != null || state.getResolutionTargetAt() == null || triggerValue == null) {
                    return new TriggerEval(false, false, 0);
                }
                double pct = slaTimerService.percentUsed(now, state.getResolutionTargetAt(), policy.getResolutionMinutes(), schedule);
                return new TriggerEval(pct >= triggerValue.doubleValue(), now.isAfter(state.getResolutionTargetAt()), pct);
            }
            case "AT_BREACH" -> {
                if (state.getResolutionAt() != null || state.getResolutionTargetAt() == null) return new TriggerEval(false, false, 0);
                boolean breached = now.isAfter(state.getResolutionTargetAt());
                return new TriggerEval(breached, true, breached ? 100 : 0);
            }
            case "AFTER_BREACH_MINUTES" -> {
                if (state.getResolutionAt() != null || state.getResolutionTargetAt() == null || triggerValue == null) {
                    return new TriggerEval(false, false, 0);
                }
                LocalDateTime threshold = state.getResolutionTargetAt().plusMinutes(triggerValue.longValue());
                boolean due = now.isAfter(threshold);
                return new TriggerEval(due, true, due ? 100 : 0);
            }
            default -> {
                log.warn("[SLA] Unknown trigger_type '{}' on step {}", step.getTriggerType(), step.getId());
                return new TriggerEval(false, false, 0);
            }
        }
    }
}
