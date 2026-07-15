package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final com.turbotikects.turbotikectsserver.services.WorkflowActionTestService workflowActionTestService;

    public TemplateController(TemplateService templateService,
                               com.turbotikects.turbotikectsserver.services.WorkflowActionTestService workflowActionTestService) {
        this.templateService = templateService;
        this.workflowActionTestService = workflowActionTestService;
    }

    // Open to any authenticated user — needed to render ticket forms for end users
    @GetMapping("/{id}")
    public TemplateWithLayoutDto getWithLayout(@PathVariable Long id) {
        return templateService.getWithLayout(id);
    }

    // Open to any authenticated user — needed for template selection in CreateTicketPage
    @GetMapping
    public List<TemplateSummaryDto> getAll() {
        return templateService.getAll();
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping
    public TemplateWithLayoutDto create(@RequestBody SaveLayoutRequestDto dto) {
        return templateService.create(dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PutMapping("/{id}")
    public TemplateWithLayoutDto saveLayout(@PathVariable Long id, @RequestBody SaveLayoutRequestDto dto) {
        return templateService.saveLayout(id, dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PatchMapping("/{id}/set-default")
    public TemplateSummaryDto setDefault(@PathVariable Long id) {
        return templateService.setDefault(id);
    }

    @RequirePermission("MANAGE_FIELDS")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/{id}/ai-suggest")
    public Map<String, Object> aiSuggest(@PathVariable Long id,
                                         @RequestBody AiSuggestLayoutRequestDto dto)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiSuggestLayout(id, dto);
    }

    /**
     * FEAT-06 Phase 5 — AI Workflow Builder. Not tied to a specific template (no {id} needed to
     * draft one) since generation only needs the pasted docs + which ticket fields are available;
     * the admin picks which template/node to save the reviewed draft into afterward via the normal
     * PUT /templates/{id} save path (Phase 4's editor + this template's own secret handling).
     */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-suggest-workflow-action")
    public Map<String, Object> aiSuggestWorkflowAction(@RequestBody AiWorkflowActionDraftRequestDto dto)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiSuggestWorkflowAction(dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-suggest-mcp-action")
    public Map<String, Object> aiSuggestMcpAction(@RequestBody com.turbotikects.turbotikectsserver.dto.AiMcpActionDraftRequestDto dto)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiSuggestMcpAction(dto);
    }

    /** FEAT-06 Phase 7 — "test this call now": runs a Designer draft (external_api or mcp_tool) live, no persistence. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/test-workflow-action")
    public com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult testWorkflowAction(
            @RequestBody com.turbotikects.turbotikectsserver.dto.WorkflowActionTestRequestDto dto) {
        return workflowActionTestService.test(dto);
    }
}
