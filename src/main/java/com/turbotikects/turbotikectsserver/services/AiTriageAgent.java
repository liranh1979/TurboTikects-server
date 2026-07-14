package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predictive layer on top of the deterministic SLA clock: the clock can only report what's true
 * right now (elapsed %, breach state), but can't see qualitative signals like problem complexity,
 * reassignment churn, or a ticket going quiet despite time passing. Scores those into a 0-100
 * {@code ai_breach_risk_score} + one-line {@code ai_risk_reason}, surfaced by {@code AiRiskIndicator}
 * only above a threshold — a warning light, not shown for every ticket. Wired into
 * {@link SlaSchedulerService}'s same 10-second tick, batch-capped so a backlog after downtime paces
 * itself across ticks instead of firing dozens of LLM calls in one pass.
 *
 * Follows {@code DashboardService}'s soft-fail pattern (build a fallback, null-check the active
 * provider, wrap everything in try/catch) rather than {@code TicketAiService}'s unguarded parse —
 * a bad LLM response here must never crash the shared scheduler tick that also drives escalation.
 */
@Slf4j
@Service
public class AiTriageAgent {

    private static final int STALE_MINUTES = 30;
    private static final int BATCH_LIMIT = 10;

    private final AiSettingsService aiSettingsService;
    private final TicketSlaStateRepository stateRepo;
    private final TicketRepository ticketRepo;
    private final TicketActivityLogRepository activityLogRepo;
    private final SlaPolicyRepository policyRepo;
    private final SlaTimerService slaTimerService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiTriageAgent(AiSettingsService aiSettingsService, TicketSlaStateRepository stateRepo,
                          TicketRepository ticketRepo, TicketActivityLogRepository activityLogRepo,
                          SlaPolicyRepository policyRepo, SlaTimerService slaTimerService) {
        this.aiSettingsService = aiSettingsService;
        this.stateRepo = stateRepo;
        this.ticketRepo = ticketRepo;
        this.activityLogRepo = activityLogRepo;
        this.policyRepo = policyRepo;
        this.slaTimerService = slaTimerService;
    }

    /**
     * Synchronized for the same reason as {@code SlaSchedulerService.runEscalationCheck} — the
     * scheduled tick and the startup catch-up listener can land close enough together that both
     * would otherwise read the same "stale" candidate before either writes its result, each
     * independently paying for a full (slow — real LLM calls here run tens of seconds) scoring pass
     * on the very same ticket. Caught live: ticket 15 was scored twice 30 seconds apart with two
     * different scores before this guard was added.
     */
    public synchronized void scoreStaleTickets() {
        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) return; // no provider configured — leave existing scores alone, don't churn

        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        List<TicketSlaStateEntity> candidates = stateRepo
                .findByAiBreachRiskScoreIsNullOrAiLastScoredAtBefore(staleBefore).stream()
                .filter(s -> s.getSlaPolicyId() != null)
                .limit(BATCH_LIMIT)
                .toList();

        for (TicketSlaStateEntity state : candidates) {
            try {
                scoreOne(ai, state);
            } catch (Exception e) {
                log.warn("[SLA-AI] Unexpected failure scoring ticket {}: {}", state.getTicketId(), e.getMessage());
            }
        }
    }

    private void scoreOne(AiSettingsEntity ai, TicketSlaStateEntity state) {
        TicketEntity ticket = ticketRepo.findById(state.getTicketId()).orElse(null);
        if (ticket == null || SlaTimerService.isResolvedStatus(ticket.getStatus()) || state.getResolutionAt() != null) {
            // Done or gone — nothing left to predict. The candidate query is an OR of "score IS NULL"
            // and "stale by time"; leaving the score null here (as an earlier version of this method
            // did) would keep matching the IsNull branch forever regardless of aiLastScoredAt, so a
            // resolved ticket that was never LLM-scored would get re-selected and re-saved on *every*
            // single tick indefinitely — a real bug caught only once Phase 5's dashboard fingerprint
            // (which hashes MAX(ticket_sla_state.updated_at)) never stabilized because of exactly this
            // churn. Score 0 ("no further risk — resolved") is an explicit non-null sentinel so it
            // falls back to the same 30-minute staleness cadence as everything else.
            state.setAiBreachRiskScore(0);
            state.setAiRiskReason(null);
            state.setAiLastScoredAt(LocalDateTime.now());
            stateRepo.save(state);
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(ticket, state);

            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("You are an SLA breach-risk triage assistant for a helpdesk ticketing system. "
                    + "Given a ticket's current state and activity signals, estimate the probability (0-100) that "
                    + "it will breach its resolution SLA, on top of what a plain countdown already shows. Weigh "
                    + "qualitative risk factors a countdown can't see: problem complexity implied by the "
                    + "description, how much reassignment churn has happened, and how little recent activity "
                    + "there's been despite time passing. Respond with ONLY a valid JSON object, no markdown, no "
                    + "code fences, no explanation, matching exactly this shape: "
                    + "{\"riskScore\": <integer 0-100>, \"reason\": \"<one short sentence>\"}");

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent(mapper.writeValueAsString(payload));

            String raw = aiSettingsService.sendLlmRequest(ai, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            Map<String, Object> parsed = mapper.readValue(cleaned, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            int score = clampScore(parsed.get("riskScore"));
            String reason = parsed.get("reason") != null ? String.valueOf(parsed.get("reason")) : "";

            state.setAiBreachRiskScore(score);
            state.setAiRiskReason(reason);
        } catch (Exception e) {
            log.warn("[SLA-AI] Failed to score ticket {}: {}", state.getTicketId(), e.getMessage());
            // Leave any previous score/reason as-is — a transient LLM/parse failure shouldn't erase a
            // still-plausible prior reading. Only the staleness stamp below changes, which rate-limits retries.
        } finally {
            state.setAiLastScoredAt(LocalDateTime.now());
            stateRepo.save(state);
        }
    }

    private Map<String, Object> buildPayload(TicketEntity ticket, TicketSlaStateEntity state) {
        List<TicketActivityLogEntity> activity = activityLogRepo.findByTicketIdOrderByCreatedAtDesc(state.getTicketId());
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        long recentActivityCount = activity.stream().filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(last24h)).count();
        long reassignmentCount = activity.stream().filter(a -> a.getChanges() != null
                && (a.getChanges().containsKey("responsibleUserId") || a.getChanges().containsKey("responsibleGroupId"))).count();

        Double resolutionPercentUsed = null;
        SlaPolicyEntity policy = policyRepo.findById(state.getSlaPolicyId()).orElse(null);
        if (policy != null && state.getResolutionTargetAt() != null) {
            List<HoursOfOperationEntity> schedule = slaTimerService.resolveSchedule(ticket.getCompanyId(), policy.isBusinessHours());
            resolutionPercentUsed = slaTimerService.percentUsed(LocalDateTime.now(), state.getResolutionTargetAt(),
                    policy.getResolutionMinutes(), schedule);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", ticket.getTitle());
        payload.put("description", truncate(ticket.getDescription(), 1500));
        payload.put("priority", ticket.getPriority());
        payload.put("status", ticket.getStatus());
        payload.put("firstResponseDone", state.getFirstResponseAt() != null);
        payload.put("resolutionPercentUsed", resolutionPercentUsed);
        payload.put("totalActivityCount", activity.size());
        payload.put("recentActivityCount24h", recentActivityCount);
        payload.put("reassignmentCount", reassignmentCount);
        return payload;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static int clampScore(Object raw) {
        if (raw == null) return 0;
        double val = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(raw));
        return (int) Math.round(Math.min(100, Math.max(0, val)));
    }
}
