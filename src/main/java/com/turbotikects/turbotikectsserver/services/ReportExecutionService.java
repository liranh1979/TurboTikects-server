package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.ReportFieldDto;
import com.turbotikects.turbotikectsserver.dto.ReportSummaryDto;
import com.turbotikects.turbotikectsserver.entitys.ReportDefinitionEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportRunEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.repositorys.ReportDefinitionRepository;
import com.turbotikects.turbotikectsserver.repositorys.ReportRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The single pipeline behind both a manual "Test ▶" click and a scheduled fire — see
 * V2/repoets/feat-05-01-data-model.html's lifecycle diagram and
 * feat-05-03-report-management-ui.html's architecture diagram. Both callers just pass a
 * different `triggeredBy` value; everything downstream (query, export, AI summary) is identical.
 */
@Slf4j
@Service
public class ReportExecutionService {

    private final ReportDefinitionRepository reportRepo;
    private final ReportRunRepository runRepo;
    private final ReportQueryService reportQueryService;
    private final ReportExportService reportExportService;
    private final ReportSummaryService reportSummaryService;

    public ReportExecutionService(ReportDefinitionRepository reportRepo,
                                   ReportRunRepository runRepo,
                                   ReportQueryService reportQueryService,
                                   ReportExportService reportExportService,
                                   ReportSummaryService reportSummaryService) {
        this.reportRepo = reportRepo;
        this.runRepo = runRepo;
        this.reportQueryService = reportQueryService;
        this.reportExportService = reportExportService;
        this.reportSummaryService = reportSummaryService;
    }

    @SuppressWarnings("unchecked")
    public ReportRunEntity runReport(Long reportDefinitionId, String triggeredBy) {
        ReportDefinitionEntity report = reportRepo.findById(reportDefinitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        ReportRunEntity run = new ReportRunEntity();
        run.setReportDefinitionId(reportDefinitionId);
        run.setTriggeredBy(triggeredBy);
        run.setStatus("running");
        run = runRepo.save(run); // persist first so generated files can be keyed off a real run id

        try {
            Map<String, Object> querySpec = report.getQuerySpec() != null ? report.getQuerySpec() : Map.of();
            List<String> selectedFields = (List<String>) querySpec.getOrDefault("selectedFields", List.of());
            Map<String, Object> conditions = (Map<String, Object>) querySpec.getOrDefault("conditions", Map.of());

            List<TicketEntity> matched = reportQueryService.findMatching(conditions);
            List<Map<String, Object>> rows = matched.stream()
                    .map(t -> reportQueryService.rowFor(t, selectedFields))
                    .collect(Collectors.toList());

            ReportSummaryDto summary = reportSummaryService.summarize(report.getName(), selectedFields, rows);

            List<String> exportFormats = report.getExportFormats() != null && !report.getExportFormats().isEmpty()
                    ? report.getExportFormats() : List.of("csv", "pdf");
            Map<String, String> fieldLabels = reportQueryService.getTicketFieldCatalog().stream()
                    .collect(Collectors.toMap(ReportFieldDto::getFieldKey, ReportFieldDto::getLabel, (a, b) -> a));

            if (exportFormats.contains("csv")) {
                run.setCsvPath(reportExportService.writeCsv(run.getId(), selectedFields, rows));
            }
            if (exportFormats.contains("pdf")) {
                run.setPdfPath(reportExportService.writePdf(run.getId(), report.getName(), selectedFields,
                        fieldLabels, rows, summary, summarizeConditionsForRecap(conditions)));
            }

            run.setRowCount(rows.size());
            run.setStatus(rows.isEmpty() ? "no_data" : "success");
            if (summary != null) {
                run.setAiSummary(summary.getSummary());
                run.setAiTips(new ArrayList<>(summary.getTips()));
            }
            run.setCompletedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("[ReportExecutionService] Run failed for report {} ({})", reportDefinitionId, triggeredBy, e);
            run.setStatus("failed");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
        }
        return runRepo.save(run);
    }

    /** Best-effort plain-English recap of the top-level conditions, shown on the "no data found"
     * PDF so an admin can see at a glance why nothing matched — not a full pretty-printer of
     * arbitrary nested AND/OR trees, just the flat leaves. */
    @SuppressWarnings("unchecked")
    private String summarizeConditionsForRecap(Map<String, Object> node) {
        List<String> parts = new ArrayList<>();
        collectLeafSummaries(node, parts);
        return String.join(", ", parts);
    }

    @SuppressWarnings("unchecked")
    private void collectLeafSummaries(Map<String, Object> node, List<String> out) {
        if (node == null) return;
        if (node.containsKey("combinator")) {
            for (Object child : (List<Object>) node.getOrDefault("conditions", List.of())) {
                if (child instanceof Map) collectLeafSummaries((Map<String, Object>) child, out);
            }
        } else if (node.containsKey("field")) {
            out.add(node.get("field") + " " + node.get("operator") + " " + node.get("value"));
        }
    }
}
