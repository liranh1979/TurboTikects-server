package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.dashboard.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.DashboardReportCacheEntity;
import com.turbotikects.turbotikectsserver.repositorys.DashboardReportCacheRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.WorkflowItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardService {

    private static final String DASHBOARD_ID = "tickets_dashboard";
    private static final int MIN_CONFIDENCE_FOR_SOLUTION = 60;

    private final TicketRepository ticketRepository;
    private final WorkflowItemRepository workflowItemRepository;
    private final DashboardReportCacheRepository cacheRepository;
    private final AiSettingsService aiSettingsService;
    private final CsatDashboardService csatDashboardService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashboardService(TicketRepository ticketRepository,
                             WorkflowItemRepository workflowItemRepository,
                             DashboardReportCacheRepository cacheRepository,
                             AiSettingsService aiSettingsService,
                             CsatDashboardService csatDashboardService) {
        this.ticketRepository = ticketRepository;
        this.workflowItemRepository = workflowItemRepository;
        this.cacheRepository = cacheRepository;
        this.aiSettingsService = aiSettingsService;
        this.csatDashboardService = csatDashboardService;
    }

    public TicketsDashboardResponseDto getDashboard(Integer companyId) {
        DashboardSeriesDto ticketSeries = buildSeries(
                ticketRepository.countCreatedByHourSince(LocalDateTime.now().minusHours(24), companyId),
                ticketRepository.countCreatedByDaySince(LocalDateTime.now().minusDays(7), companyId),
                ticketRepository.countCreatedByDaySince(LocalDateTime.now().minusDays(30), companyId),
                ticketRepository.countByStatus(companyId));

        DashboardSeriesDto actionItemSeries = buildSeries(
                workflowItemRepository.countCreatedByHourSince(LocalDateTime.now().minusHours(24), companyId),
                workflowItemRepository.countCreatedByDaySince(LocalDateTime.now().minusDays(7), companyId),
                workflowItemRepository.countCreatedByDaySince(LocalDateTime.now().minusDays(30), companyId),
                workflowItemRepository.countByStatus(companyId));

        String fingerprint = computeFingerprint(companyId);
        DashboardAiReportDto aiReport = getOrGenerateAiReport(companyId, fingerprint, ticketSeries, actionItemSeries);

        TicketsDashboardResponseDto response = new TicketsDashboardResponseDto();
        response.setTickets(ticketSeries);
        response.setActionItems(actionItemSeries);
        response.setAiReport(aiReport);
        response.setCsat(csatDashboardService.getDashboard(companyId));
        return response;
    }

    // ── SERIES BUILDING ──────────────────────────────────────────────────────

    private DashboardSeriesDto buildSeries(List<Object[]> hourlyRows, List<Object[]> weeklyRows,
                                            List<Object[]> monthlyRows, List<Object[]> statusRows) {
        DashboardSeriesDto dto = new DashboardSeriesDto();
        dto.setDay(toPoints(hourlyRows, true));
        dto.setWeek(toPoints(weeklyRows, false));
        dto.setMonth(toPoints(monthlyRows, false));
        dto.setByStatus(toStatusMap(statusRows));
        return dto;
    }

    private List<DashboardTimeSeriesPointDto> toPoints(List<Object[]> rows, boolean isHourBucket) {
        List<DashboardTimeSeriesPointDto> points = new ArrayList<>();
        for (Object[] row : rows) {
            String bucket = isHourBucket
                    ? String.format("%02d:00", ((Number) row[0]).intValue())
                    : String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            points.add(new DashboardTimeSeriesPointDto(bucket, count));
        }
        return points;
    }

    private Map<String, Long> toStatusMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    // ── FINGERPRINT ───────────────────────────────────────────────────────────

    private String computeFingerprint(Integer companyId) {
        Object[] ticketRow = ticketRepository.fingerprintData(companyId).get(0);
        Object[] workflowRow = workflowItemRepository.fingerprintData(companyId).get(0);
        return "t:" + ((Number) ticketRow[0]).longValue() + ":" + ticketRow[1]
                + "|w:" + ((Number) workflowRow[0]).longValue() + ":" + workflowRow[1];
    }

    // ── AI REPORT (cached) ────────────────────────────────────────────────────

    private DashboardAiReportDto getOrGenerateAiReport(Integer companyId, String fingerprint,
                                                          DashboardSeriesDto tickets, DashboardSeriesDto actionItems) {
        Optional<DashboardReportCacheEntity> cached = cacheRepository.findByDashboardIdAndCompanyId(DASHBOARD_ID, companyId);
        if (cached.isPresent() && fingerprint.equals(cached.get().getFingerprint())) {
            DashboardAiReportDto dto = mapper.convertValue(cached.get().getReportJson(), DashboardAiReportDto.class);
            dto.setCached(true);
            dto.setGeneratedAt(cached.get().getGeneratedAt());
            return dto;
        }

        DashboardAiReportDto fresh = callLlmForReport(tickets, actionItems);

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

    private DashboardAiReportDto callLlmForReport(DashboardSeriesDto tickets, DashboardSeriesDto actionItems) {
        DashboardAiReportDto fallback = new DashboardAiReportDto();
        fallback.setSummary("AI report unavailable");
        fallback.setRecurringProblems(Collections.emptyList());

        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) return fallback;

        try {
            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("You are an IT support operations analyst. You will be given aggregated ticket and " +
                    "task counts. Identify recurring problems in the data. For each recurring problem, only " +
                    "include a \"solution\" if you are at least 60% confident it is correct and actionable; " +
                    "otherwise set \"solution\" to null and just describe the problem so it can be flagged for " +
                    "human review — never invent a solution you are not confident about. Respond with ONLY a " +
                    "valid JSON object, no markdown, no explanation, matching exactly this shape: " +
                    "{\"summary\": string, \"recurringProblems\": [{\"description\": string, " +
                    "\"confidencePercent\": number, \"solution\": string|null}]}");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tickets", Map.of("byStatus", tickets.getByStatus(), "last7Days", tickets.getWeek(), "last30Days", tickets.getMonth()));
            payload.put("actionItems", Map.of("byStatus", actionItems.getByStatus(), "last7Days", actionItems.getWeek(), "last30Days", actionItems.getMonth()));

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent(mapper.writeValueAsString(payload));

            String raw = aiSettingsService.sendLlmRequest(ai, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

            DashboardAiReportDto parsed = mapper.readValue(cleaned, DashboardAiReportDto.class);
            if (parsed.getRecurringProblems() == null) parsed.setRecurringProblems(Collections.emptyList());

            // Never trust the model alone — force null solutions below the confidence threshold.
            for (RecurringProblemDto problem : parsed.getRecurringProblems()) {
                if (problem.getConfidencePercent() < MIN_CONFIDENCE_FOR_SOLUTION) {
                    problem.setSolution(null);
                }
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to generate dashboard AI report: {}", e.getMessage());
            return fallback;
        }
    }
}
