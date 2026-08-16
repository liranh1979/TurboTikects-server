package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.ReportDefinitionDto;
import com.turbotikects.turbotikectsserver.dto.ReportRunDto;
import com.turbotikects.turbotikectsserver.dto.SaveReportRequestDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.entitys.ReportRunEntity;
import com.turbotikects.turbotikectsserver.repositorys.ReportRunRepository;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.FileStorageService;
import com.turbotikects.turbotikectsserver.services.ReportExecutionService;
import com.turbotikects.turbotikectsserver.services.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequirePermission("MANAGE_REPORTS")
public class ReportController {

    private final ReportService reportService;
    private final ReportExecutionService reportExecutionService;
    private final ReportRunRepository runRepo;
    private final FileStorageService fileStorageService;

    public ReportController(ReportService reportService,
                             ReportExecutionService reportExecutionService,
                             ReportRunRepository runRepo,
                             FileStorageService fileStorageService) {
        this.reportService = reportService;
        this.reportExecutionService = reportExecutionService;
        this.runRepo = runRepo;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public List<ReportDefinitionDto> getAll() {
        return reportService.getAll();
    }

    @GetMapping("/{id}")
    public ReportDefinitionDto getOne(@PathVariable Long id) {
        return reportService.getOne(id);
    }

    @PostMapping
    public ReportDefinitionDto create(@RequestBody SaveReportRequestDto dto, HttpServletRequest request) {
        return reportService.create(dto, currentUserId(request));
    }

    @PutMapping("/{id}")
    public ReportDefinitionDto update(@PathVariable Long id, @RequestBody SaveReportRequestDto dto) {
        return reportService.update(id, dto);
    }

    @PatchMapping("/{id}/active")
    public void setActive(@PathVariable Long id, @RequestParam boolean active) {
        reportService.setActive(id, active);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reportService.delete(id);
    }

    @GetMapping("/{id}/runs")
    public List<ReportRunDto> getRuns(@PathVariable Long id) {
        return reportService.getRuns(id);
    }

    @GetMapping("/groups")
    public List<Map<String, Object>> getAssignableGroups() {
        return reportService.getAssignableGroups();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getAssignableUsers() {
        return reportService.getAssignableUsers();
    }

    /** Manual "Test ▶" — runs the exact same pipeline a scheduled fire uses, synchronously
     * (matches this codebase's existing convention for single-LLM-call AI endpoints, e.g.
     * TemplateService.aiSuggestLayout — not a TaskProgressService background job, which this
     * codebase reserves for genuinely long multi-record operations). */
    @PostMapping("/{id}/test-run")
    public ReportRunDto testRun(@PathVariable Long id) {
        reportService.getOne(id); // 404s cleanly if the report doesn't exist
        ReportRunEntity run = reportExecutionService.runReport(id, "manual");
        return toRunDto(run);
    }

    @GetMapping("/runs/{runId}/download/{format}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long runId, @PathVariable String format) throws Exception {
        ReportRunEntity run = runRepo.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report run not found"));
        String path = "csv".equalsIgnoreCase(format) ? run.getCsvPath() : "pdf".equalsIgnoreCase(format) ? run.getPdfPath() : null;
        if (path == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + format + " file for this run");
        }
        byte[] data = fileStorageService.retrieve(path);
        String filename = "report-" + runId + "." + format.toLowerCase();
        MediaType contentType = "csv".equalsIgnoreCase(format) ? MediaType.parseMediaType("text/csv") : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    private ReportRunDto toRunDto(ReportRunEntity r) {
        ReportRunDto dto = new ReportRunDto();
        dto.setId(r.getId());
        dto.setReportDefinitionId(r.getReportDefinitionId());
        dto.setTriggeredBy(r.getTriggeredBy());
        dto.setRowCount(r.getRowCount());
        dto.setStatus(r.getStatus());
        dto.setAiSummary(r.getAiSummary());
        dto.setAiTips(r.getAiTips());
        dto.setCsvPath(r.getCsvPath());
        dto.setPdfPath(r.getPdfPath());
        dto.setStartedAt(r.getStartedAt());
        dto.setCompletedAt(r.getCompletedAt());
        dto.setErrorMessage(r.getErrorMessage());
        return dto;
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }
}
