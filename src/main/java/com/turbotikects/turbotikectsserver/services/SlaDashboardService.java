package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.dashboard.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.DashboardReportCacheEntity;
import com.turbotikects.turbotikectsserver.repositorys.DashboardReportCacheRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketSlaStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The one additional AI feature explicitly scoped into the FEAT-02 plan (decision #9) beyond the
 * core breach-risk agent — a near-direct copy of {@link CsatDashboardService}'s already-proven
 * fingerprint-cache pattern, since the deterministic aggregates here (breach counts by priority)
 * are cheap to recompute every call, and only the LLM-generated narrative needs caching.
 */
@Slf4j
@Service
public class SlaDashboardService {

    private static final String DASHBOARD_ID = "sla_performance";

    private final TicketSlaStateRepository stateRepo;
    private final DashboardReportCacheRepository cacheRepository;
    private final AiSettingsService aiSettingsService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlaDashboardService(TicketSlaStateRepository stateRepo, DashboardReportCacheRepository cacheRepository,
                                AiSettingsService aiSettingsService) {
        this.stateRepo = stateRepo;
        this.cacheRepository = cacheRepository;
        this.aiSettingsService = aiSettingsService;
    }

    public SlaDashboardResponseDto getDashboard(Integer companyId) {
        List<SlaPriorityStatDto> priorityStats = new ArrayList<>();
        for (Object[] row : stateRepo.breachStatsByPriority(companyId)) {
            SlaPriorityStatDto dto = new SlaPriorityStatDto();
            dto.setPriority((String) row[0]);
            long total = ((Number) row[1]).longValue();
            long resolvedCount = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long frBreached = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            long resBreached = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            dto.setTotal(total);
            dto.setResolvedCount(resolvedCount);
            dto.setFirstResponseBreached(frBreached);
            dto.setResolutionBreached(resBreached);
            dto.setResolutionBreachRatePercent(total == 0 ? 0.0 : round1((resBreached * 100.0) / total));
            priorityStats.add(dto);
        }
        priorityStats.sort(Comparator.comparingInt(s -> priorityRank(s.getPriority())));

        String fingerprint = computeFingerprint(companyId);
        SlaAiInsightDto aiInsight = getOrGenerateAiInsight(companyId, fingerprint, priorityStats);

        SlaDashboardResponseDto response = new SlaDashboardResponseDto();
        response.setPriorityStats(priorityStats);
        response.setAiInsight(aiInsight);
        return response;
    }

    private int priorityRank(String p) {
        return switch (p == null ? "" : p.toLowerCase()) {
            case "critical" -> 0;
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            default -> 4;
        };
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String computeFingerprint(Integer companyId) {
        Object[] row = stateRepo.fingerprintData(companyId).get(0);
        long cnt = ((Number) row[0]).longValue();
        Object lastUpdate = row[1];
        return "s:" + cnt + ":" + lastUpdate;
    }

    private SlaAiInsightDto getOrGenerateAiInsight(Integer companyId, String fingerprint,
                                                     List<SlaPriorityStatDto> priorityStats) {
        Optional<DashboardReportCacheEntity> cached = cacheRepository.findByDashboardIdAndCompanyId(DASHBOARD_ID, companyId);
        if (cached.isPresent() && fingerprint.equals(cached.get().getFingerprint())) {
            SlaAiInsightDto dto = mapper.convertValue(cached.get().getReportJson(), SlaAiInsightDto.class);
            dto.setCached(true);
            dto.setGeneratedAt(cached.get().getGeneratedAt());
            return dto;
        }

        SlaAiInsightDto fresh = callLlmForInsight(priorityStats);

        // Snapshot to JSON while cached/generatedAt are still false/null — this service's local
        // ObjectMapper has no JSR310 module, so a non-null LocalDateTime would fail to serialize.
        Map<String, Object> snapshot = mapper.convertValue(fresh, new TypeReference<>() {});
        LocalDateTime now = LocalDateTime.now();

        DashboardReportCacheEntity entity = cached.orElseGet(DashboardReportCacheEntity::new);
        entity.setCompanyId(companyId);
        entity.setDashboardId(DASHBOARD_ID);
        entity.setFingerprint(fingerprint);
        entity.setReportJson(snapshot);
        entity.setGeneratedAt(now);
        cacheRepository.save(entity);

        fresh.setCached(false);
        fresh.setGeneratedAt(now);
        return fresh;
    }

    private SlaAiInsightDto callLlmForInsight(List<SlaPriorityStatDto> priorityStats) {
        SlaAiInsightDto fallback = new SlaAiInsightDto();
        fallback.setSummary("AI insight unavailable");
        fallback.setFindings(Collections.emptyList());

        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) return fallback;

        boolean hasAnyData = priorityStats.stream().anyMatch(s -> s.getTotal() > 0);
        if (!hasAnyData) {
            fallback.setSummary("Not enough SLA data yet to generate an insight.");
            return fallback;
        }

        try {
            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("You are an SLA performance analyst for a helpdesk ticketing system. You will be "
                    + "given aggregate SLA breach statistics broken down by ticket priority — total tickets "
                    + "tracked, how many resolved, how many breached first-response and resolution SLAs, and the "
                    + "resolution breach rate percentage. Write a concise summary of SLA health across priority "
                    + "tiers, and 2-4 concrete, actionable findings, each grounded in the specific numbers given "
                    + "(cite the priority and rate that supports it). Do not fabricate numbers not present in the "
                    + "input. If there is too little data to draw conclusions, say so plainly instead of "
                    + "speculating. Respond with ONLY a valid JSON object, no markdown, no explanation, matching "
                    + "exactly this shape: {\"summary\": string, \"findings\": [{\"observation\": string, "
                    + "\"recommendation\": string}]}");

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent(mapper.writeValueAsString(priorityStats));

            String raw = aiSettingsService.sendLlmRequest(ai, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

            SlaAiInsightDto parsed = mapper.readValue(cleaned, SlaAiInsightDto.class);
            if (parsed.getFindings() == null) parsed.setFindings(Collections.emptyList());
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to generate SLA AI insight: {}", e.getMessage());
            return fallback;
        }
    }
}
