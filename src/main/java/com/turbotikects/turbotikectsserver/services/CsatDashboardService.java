package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.dashboard.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.DashboardReportCacheEntity;
import com.turbotikects.turbotikectsserver.repositorys.DashboardReportCacheRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketCsatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class CsatDashboardService {

    private static final String DASHBOARD_ID = "csat_dashboard";

    private final TicketCsatRepository csatRepo;
    private final DashboardReportCacheRepository cacheRepository;
    private final AiSettingsService aiSettingsService;
    private final ObjectMapper mapper = new ObjectMapper();

    public CsatDashboardService(TicketCsatRepository csatRepo, DashboardReportCacheRepository cacheRepository,
                                 AiSettingsService aiSettingsService) {
        this.csatRepo = csatRepo;
        this.cacheRepository = cacheRepository;
        this.aiSettingsService = aiSettingsService;
    }

    private record CsatAggregates(double avgScore, double responseRate, double lowScorePercent,
                                    Map<Integer, Long> distribution) {}

    private CsatAggregates computeAggregates(Integer companyId) {
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        Map<Integer, Long> distribution = toDistributionMap(csatRepo.scoreDistributionSince(since30d, companyId));
        long totalResponses = distribution.values().stream().mapToLong(Long::longValue).sum();
        long lowResponses = distribution.getOrDefault(1, 0L) + distribution.getOrDefault(2, 0L);

        double avgScore = totalResponses == 0 ? 0.0 : distribution.entrySet().stream()
                .mapToDouble(e -> e.getKey() * e.getValue()).sum() / totalResponses;
        double lowScorePercent = totalResponses == 0 ? 0.0 : (lowResponses * 100.0) / totalResponses;

        Object[] rateRow = csatRepo.responseRateSince(since30d, companyId).get(0);
        long sent = ((Number) rateRow[0]).longValue();
        long responded = rateRow[1] != null ? ((Number) rateRow[1]).longValue() : 0L;
        double responseRate = sent == 0 ? 0.0 : (responded * 100.0) / sent;

        return new CsatAggregates(avgScore, responseRate, lowScorePercent, distribution);
    }

    /** Always fast — pure DB reads, never an LLM call. See {@link #refreshAiAnalysisIfStale}. */
    public CsatDashboardResponseDto getDashboard(Integer companyId) {
        CsatAggregates agg = computeAggregates(companyId);

        List<CsatLowScoreTicketDto> lowScoreTickets = new ArrayList<>();
        for (Object[] row : csatRepo.lowScoreTickets(companyId)) {
            CsatLowScoreTicketDto dto = new CsatLowScoreTicketDto();
            dto.setTicketId(((Number) row[0]).longValue());
            dto.setTitle((String) row[1]);
            dto.setAgent(row[2] != null ? (String) row[2] : "Unassigned");
            dto.setScore(((Number) row[3]).intValue());
            dto.setComment((String) row[4]);
            lowScoreTickets.add(dto);
        }

        CsatDashboardResponseDto response = new CsatDashboardResponseDto();
        response.setAvgScore(round1(agg.avgScore()));
        response.setResponseRate(round1(agg.responseRate()));
        response.setLowScorePercent(round1(agg.lowScorePercent()));
        response.setDistribution(agg.distribution());
        response.setLowScoreTickets(lowScoreTickets);
        response.setAiAnalysis(getCachedAiAnalysis(companyId));
        return response;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private Map<Integer, Long> toDistributionMap(List<Object[]> rows) {
        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) map.put(i, 0L);
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            map.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private String computeFingerprint(Integer companyId) {
        Object[] row = csatRepo.fingerprintData(companyId).get(0);
        long cnt = ((Number) row[0]).longValue();
        Object lastResponse = row[1];
        return "c:" + cnt + ":" + lastResponse;
    }

    private CsatAiAnalysisDto getCachedAiAnalysis(Integer companyId) {
        String fingerprint = computeFingerprint(companyId);
        Optional<DashboardReportCacheEntity> cached = cacheRepository.findByDashboardIdAndCompanyId(DASHBOARD_ID, companyId);

        if (cached.isEmpty()) {
            CsatAiAnalysisDto pending = new CsatAiAnalysisDto();
            pending.setSummary("AI analysis is being generated — check back shortly.");
            pending.setImprovementPoints(Collections.emptyList());
            pending.setCached(false);
            pending.setStale(false);
            return pending;
        }

        CsatAiAnalysisDto dto = mapper.convertValue(cached.get().getReportJson(), CsatAiAnalysisDto.class);
        if (dto.getImprovementPoints() == null) dto.setImprovementPoints(Collections.emptyList());
        dto.setCached(true);
        dto.setGeneratedAt(cached.get().getGeneratedAt());
        dto.setStale(!fingerprint.equals(cached.get().getFingerprint()));
        return dto;
    }

    /** Called only by {@link DashboardReportSchedulerService}. Synchronous LLM call — safe here since this never runs on a request thread. */
    public void refreshAiAnalysisIfStale(Integer companyId) {
        String fingerprint = computeFingerprint(companyId);
        Optional<DashboardReportCacheEntity> cached = cacheRepository.findByDashboardIdAndCompanyId(DASHBOARD_ID, companyId);
        if (cached.isPresent() && fingerprint.equals(cached.get().getFingerprint())) return; // already current

        CsatAggregates agg = computeAggregates(companyId);
        CsatAiAnalysisDto fresh = callLlmForAnalysis(agg.avgScore(), agg.responseRate(), agg.lowScorePercent(), agg.distribution(), companyId);

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
    }

    private CsatAiAnalysisDto callLlmForAnalysis(double avgScore, double responseRate, double lowScorePercent,
                                                   Map<Integer, Long> distribution, Integer companyId) {
        CsatAiAnalysisDto fallback = new CsatAiAnalysisDto();
        fallback.setSummary("AI analysis unavailable");
        fallback.setImprovementPoints(Collections.emptyList());

        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) return fallback;

        try {
            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("You are a customer-experience analyst for an IT support organization. You will be " +
                    "given aggregate CSAT (customer satisfaction) survey data — average score, response rate, the " +
                    "1-5 star distribution, and verbatim comments from low-scoring (1-2 star) responses. Write a " +
                    "concise summary of how the support organization is functioning based on this data, and 3-5 " +
                    "concrete, actionable improvement points, each grounded in specific evidence from the input " +
                    "(cite the comment or number that supports it). Do not fabricate data, comments, or numbers " +
                    "not present in the input. If there is too little data to draw conclusions, say so plainly " +
                    "instead of speculating. Respond with ONLY a valid JSON object, no markdown, no explanation, " +
                    "matching exactly this shape: {\"summary\": string, \"improvementPoints\": " +
                    "[{\"point\": string, \"evidence\": string}]}");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("avgScore", avgScore);
            payload.put("responseRatePercent", responseRate);
            payload.put("lowScorePercent", lowScorePercent);
            payload.put("distribution", distribution);
            payload.put("lowScoreComments", toCommentList(csatRepo.recentLowScoreComments(companyId)));

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent(mapper.writeValueAsString(payload));

            String raw = aiSettingsService.sendLlmRequest(ai, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

            CsatAiAnalysisDto parsed = mapper.readValue(cleaned, CsatAiAnalysisDto.class);
            if (parsed.getImprovementPoints() == null) parsed.setImprovementPoints(Collections.emptyList());
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to generate CSAT AI analysis: {}", e.getMessage());
            return fallback;
        }
    }

    private List<Map<String, Object>> toCommentList(List<Object[]> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("comment", row[0]);
            m.put("score", ((Number) row[1]).intValue());
            list.add(m);
        }
        return list;
    }
}
