package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AiBuildQueryRequestDto;
import com.turbotikects.turbotikectsserver.dto.ReportFieldDto;
import com.turbotikects.turbotikectsserver.dto.ReportPreviewResultDto;
import com.turbotikects.turbotikectsserver.dto.ReportQuerySpecDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.ReportQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequirePermission("MANAGE_REPORTS")
public class ReportQueryController {

    private final ReportQueryService reportQueryService;

    public ReportQueryController(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    @GetMapping("/field-catalog")
    public List<ReportFieldDto> getFieldCatalog() {
        return reportQueryService.getTicketFieldCatalog();
    }

    @PostMapping("/ai-build-query")
    public ReportPreviewResultDto aiBuildQuery(@RequestBody AiBuildQueryRequestDto dto) {
        return reportQueryService.aiBuildQuery(dto.getPrompt());
    }

    @PostMapping("/preview-query")
    public ReportPreviewResultDto previewQuery(@RequestBody ReportQuerySpecDto dto) {
        return reportQueryService.preview(dto.getSelectedFields(), dto.getConditions());
    }
}
