package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.turbotikects.turbotikectsserver.dto.ReportSummaryDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FEAT-05.6 — AI-written summary + confidence-gated improvement tips on top of a report's
 * results. Mirrors DashboardService.callLlmForReport()'s exact shape and its confidence-gate
 * (there: MIN_CONFIDENCE_FOR_SOLUTION = 60) rather than inventing a new mechanism — see
 * V2/repoets/feat-05-06-ai-summary-agent.html.
 */
@Slf4j
@Service
public class ReportSummaryService {

    // Same threshold and reasoning as DashboardService.MIN_CONFIDENCE_FOR_SOLUTION — never trust
    // the model's own confidence self-report below this, drop the tip entirely rather than show it.
    private static final int MIN_CONFIDENCE_FOR_TIP = 60;

    // Caps the row sample sent to the LLM so a large report doesn't blow the context window —
    // same capping intent as AiSettingsService.extractValueWithLlm's response-size cap.
    private static final int MAX_SAMPLE_ROWS = 200;

    private final AiSettingsService aiSettingsService;
    // Row samples contain real LocalDateTime values (e.g. createdAt) — a plain `new ObjectMapper()`
    // has no JSR310 module and throws serializing them (caught live: see PROGRESS.md). Register it
    // explicitly rather than relying on Spring's auto-configured bean, since this class builds its
    // own ObjectMapper like every other AI-call site in this codebase does.
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public ReportSummaryService(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    /** Strips HTML from string values (e.g. a rich-text ticket description) before the LLM ever
     * sees them — found live: without this, the model reasoned about "residual HTML tags" in its
     * own summary instead of the actual ticket content, since it was fed raw markup verbatim. */
    private static Map<String, Object> stripHtmlValues(Map<String, Object> row) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s && s.indexOf('<') >= 0) {
                cleaned.put(e.getKey(), Jsoup.parse(s).text());
            } else {
                cleaned.put(e.getKey(), v);
            }
        }
        return cleaned;
    }

    /** Returns null when there's nothing to summarize (zero rows — per FEAT-05.6, the AI call is
     * skipped entirely, not called and discarded) or no AI is configured/the call failed. Callers
     * treat null identically to "no AI section" — never a crash of the underlying report run. */
    public ReportSummaryDto summarize(String reportName, List<String> selectedFields, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return null;

        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) return null;

        try {
            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("""
                    You are an IT support operations analyst reviewing a generated report's data. \
                    Write a short 2-3 sentence summary of what the data shows, and propose improvement \
                    tips. For each tip, include a confidencePercent (0-100) — only include a tip if you \
                    are genuinely confident it is correct and actionable given the data shown; \
                    otherwise leave it out entirely rather than guessing. Respond with ONLY a valid \
                    JSON object, no markdown, no explanation, matching exactly this shape: \
                    {"summary": string, "tips": [{"description": string, "confidencePercent": number}]}
                    """);

            List<Map<String, Object>> sample = rows.size() > MAX_SAMPLE_ROWS ? rows.subList(0, MAX_SAMPLE_ROWS) : rows;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reportName", reportName);
            payload.put("fields", selectedFields);
            payload.put("rowCount", rows.size());
            payload.put("sampleRows", sample.stream().map(ReportSummaryService::stripHtmlValues).collect(Collectors.toList()));

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent(mapper.writeValueAsString(payload));

            String raw = aiSettingsService.sendLlmRequest(ai, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            ReportSummaryDto parsed = mapper.readValue(cleaned, ReportSummaryDto.class);
            if (parsed.getTips() == null) parsed.setTips(new ArrayList<>());

            parsed.setTips(parsed.getTips().stream()
                    .filter(t -> t.getConfidencePercent() >= MIN_CONFIDENCE_FOR_TIP)
                    .collect(Collectors.toList()));

            return parsed;
        } catch (Exception e) {
            log.warn("[ReportSummaryService] AI summary generation failed for report '{}': {}", reportName, e.getMessage());
            return null;
        }
    }
}
