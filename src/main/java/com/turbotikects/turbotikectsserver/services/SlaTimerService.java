package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Owns the SLA clock lifecycle for a ticket: starting both clocks against the resolved policy,
 * recording first response / resolution, and pause/resume while a ticket is in "waiting" status.
 *
 * Deliberately separate from the generic {@code timer} field (TimerFieldControl/processTimerFields):
 * SLA duration is never manually entered — it's derived from the ticket's priority against an
 * admin-configured {@link SlaPolicyEntity}, and unlike processTimerFields (which was found to
 * unconditionally restamp started_at on every unrelated ticket patch), clocks here are started
 * exactly once and never reset by unrelated edits.
 */
@Slf4j
@Service
public class SlaTimerService {

    /** Public — also used by {@link SlaSchedulerService} to query open tickets via {@code findByStatusNotIn}. */
    public static final List<String> RESOLVED_STATUSES = List.of("completed", "closed", "resolved");
    private static final String WAITING_STATUS = "waiting";

    private final SlaPolicyRepository policyRepo;
    private final TicketSlaStateRepository stateRepo;
    private final HoursOfOperationRepository hooRepo;
    private final TimerCalculationService timerCalc;

    public SlaTimerService(SlaPolicyRepository policyRepo,
                            TicketSlaStateRepository stateRepo,
                            HoursOfOperationRepository hooRepo,
                            TimerCalculationService timerCalc) {
        this.policyRepo = policyRepo;
        this.stateRepo = stateRepo;
        this.hooRepo = hooRepo;
        this.timerCalc = timerCalc;
    }

    /** Whether a status value means the SLA clock should be paused (does not stop counting toward breach, just freezes elapsed time). */
    public static boolean isWaitingStatus(String status) {
        return WAITING_STATUS.equalsIgnoreCase(status);
    }

    /** Whether a status value means the ticket is done (resolution clock stops). */
    public static boolean isResolvedStatus(String status) {
        return status != null && RESOLVED_STATUSES.contains(status.toLowerCase());
    }

    // ── START ─────────────────────────────────────────────────────────────────

    @Transactional
    public void startClocks(TicketEntity ticket) {
        SlaPolicyEntity policy = resolvePolicy(ticket).orElse(null);
        if (policy == null) {
            log.debug("[SLA] No policy matches ticket {} (priority={}, templateId={}) — no SLA clock started",
                    ticket.getId(), ticket.getPriority(), ticket.getTemplateId());
            return;
        }

        List<HoursOfOperationEntity> schedule = resolveSchedule(ticket.getCompanyId(), policy.isBusinessHours());
        LocalDateTime startedAt = ticket.getCreatedAt() != null ? ticket.getCreatedAt() : LocalDateTime.now();

        TicketSlaStateEntity state = new TicketSlaStateEntity();
        state.setTicketId(ticket.getId());
        state.setSlaPolicyId(policy.getId());
        state.setFirstResponseTargetAt(
                timerCalc.calculateTarget(startedAt, policy.getFirstResponseMinutes(), "minutes", schedule));
        state.setResolutionTargetAt(
                timerCalc.calculateTarget(startedAt, policy.getResolutionMinutes(), "minutes", schedule));
        stateRepo.save(state);
    }

    private Optional<SlaPolicyEntity> resolvePolicy(TicketEntity ticket) {
        if (ticket.getPriority() == null) return Optional.empty();
        if (ticket.getTemplateId() != null) {
            Optional<SlaPolicyEntity> templateSpecific =
                    policyRepo.findByPriorityAndTemplateIdAndIsActiveTrue(ticket.getPriority(), ticket.getTemplateId());
            if (templateSpecific.isPresent()) return templateSpecific;
        }
        return policyRepo.findByPriorityAndTemplateIdIsNullAndIsActiveTrue(ticket.getPriority());
    }

    /** Public — also used by {@link SlaSchedulerService} to resolve the same schedule for escalation due-checks. */
    public List<HoursOfOperationEntity> resolveSchedule(Integer companyId, boolean businessHours) {
        if (!businessHours) return Collections.emptyList(); // 24×7 policy — TimerCalculationService treats empty as wall-clock
        List<HoursOfOperationEntity> schedule = companyId != null
                ? hooRepo.findByCompanyId(companyId)
                : hooRepo.findByCompanyIdIsNull();
        if (schedule.isEmpty()) schedule = hooRepo.findByCompanyIdIsNull();
        return schedule;
    }

    /**
     * Percent of a clock's total duration elapsed as of {@code referenceTime}, using business-hours-aware
     * elapsed time (not raw wall-clock). Canonical implementation shared with {@code TicketService}'s
     * detail-view percentage and {@link SlaSchedulerService}'s escalation due-check, so both always agree.
     */
    public double percentUsed(LocalDateTime referenceTime, LocalDateTime targetAt, int totalMinutes,
                               List<HoursOfOperationEntity> schedule) {
        if (targetAt == null || totalMinutes <= 0) return 0;
        double remainingMinutes = referenceTime.isBefore(targetAt)
                ? timerCalc.calculateElapsedBusinessMinutes(referenceTime, targetAt, schedule) : 0;
        double pct = 100.0 * (1 - remainingMinutes / totalMinutes);
        return Math.min(100.0, Math.max(0.0, pct));
    }

    // ── FIRST RESPONSE ───────────────────────────────────────────────────────

    @Transactional
    public void recordFirstResponse(Long ticketId) {
        TicketSlaStateEntity state = stateRepo.findByTicketId(ticketId).orElse(null);
        if (state == null || state.getFirstResponseAt() != null) return; // no policy, or already recorded
        LocalDateTime now = LocalDateTime.now();
        state.setFirstResponseAt(now);
        if (state.getFirstResponseTargetAt() != null && now.isAfter(state.getFirstResponseTargetAt())) {
            state.setFirstResponseBreached(true);
        }
        stateRepo.save(state);
    }

    // ── RESOLUTION ────────────────────────────────────────────────────────────

    @Transactional
    public void recordResolution(Long ticketId) {
        TicketSlaStateEntity state = stateRepo.findByTicketId(ticketId).orElse(null);
        if (state == null || state.getResolutionAt() != null) return;
        LocalDateTime now = LocalDateTime.now();
        state.setResolutionAt(now);
        if (state.getResolutionTargetAt() != null && now.isAfter(state.getResolutionTargetAt())) {
            state.setResolutionBreached(true);
        }
        stateRepo.save(state);
    }

    /** Reopening a resolved ticket resumes the resolution clock (clears the resolved stamp, not the breach flag/history). */
    @Transactional
    public void clearResolution(Long ticketId) {
        TicketSlaStateEntity state = stateRepo.findByTicketId(ticketId).orElse(null);
        if (state == null) return;
        state.setResolutionAt(null);
        stateRepo.save(state);
    }

    // ── PAUSE / RESUME ────────────────────────────────────────────────────────

    @Transactional
    public void pause(Long ticketId) {
        TicketSlaStateEntity state = stateRepo.findByTicketId(ticketId).orElse(null);
        if (state == null || state.getPausedAt() != null) return; // no policy, or already paused
        state.setPausedAt(LocalDateTime.now());
        stateRepo.save(state);
    }

    /**
     * Resumes both not-yet-reached clocks. For each clock still pending, the remaining business
     * duration as of the pause moment is derived by walking the schedule from pausedAt to the
     * clock's current (pre-pause) target — since no business time passes while paused, that walk
     * is exactly the remaining amount needed — and a fresh target is computed from "now" for that
     * remaining amount. This composes correctly across multiple pause/resume cycles because each
     * resume only ever looks at the current stored target and this pause's timestamps, never an
     * "original start" that would need tracking separately.
     */
    @Transactional
    public void resume(Long ticketId, Integer companyId) {
        TicketSlaStateEntity state = stateRepo.findByTicketId(ticketId).orElse(null);
        if (state == null || state.getPausedAt() == null) return;

        SlaPolicyEntity policy = state.getSlaPolicyId() != null
                ? policyRepo.findById(state.getSlaPolicyId()).orElse(null) : null;
        boolean businessHours = policy == null || policy.isBusinessHours();
        List<HoursOfOperationEntity> schedule = resolveSchedule(companyId, businessHours);

        LocalDateTime pausedAt = state.getPausedAt();
        LocalDateTime now = LocalDateTime.now();
        state.setTotalPausedSeconds(state.getTotalPausedSeconds() + Duration.between(pausedAt, now).getSeconds());
        state.setPausedAt(null);

        if (state.getFirstResponseAt() == null && state.getFirstResponseTargetAt() != null) {
            double remaining = timerCalc.calculateElapsedBusinessMinutes(pausedAt, state.getFirstResponseTargetAt(), schedule);
            state.setFirstResponseTargetAt(timerCalc.calculateTarget(now, remaining, "minutes", schedule));
        }
        if (state.getResolutionAt() == null && state.getResolutionTargetAt() != null) {
            double remaining = timerCalc.calculateElapsedBusinessMinutes(pausedAt, state.getResolutionTargetAt(), schedule);
            state.setResolutionTargetAt(timerCalc.calculateTarget(now, remaining, "minutes", schedule));
        }

        stateRepo.save(state);
    }
}
