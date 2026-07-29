package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-suggest-mcp-action")
    public Map<String, Object> aiSuggestMcpAction(@RequestBody com.turbotikects.turbotikectsserver.dto.AiMcpActionDraftRequestDto dto)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiSuggestMcpAction(dto);
    }

    /** External_api guided wizard Step 1 helper — see TemplateService.fetchDocumentationUrl's javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/fetch-documentation-url")
    public Map<String, Object> fetchDocumentationUrl(@RequestBody FetchDocumentationUrlRequestDto dto)
            throws IOException, InterruptedException {
        return templateService.fetchDocumentationUrl(dto);
    }

    /** External_api guided wizard Step 1 — see TemplateService.aiDiscoverEndpoints' javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-discover-endpoints")
    public Map<String, Object> aiDiscoverEndpoints(@RequestBody AiDiscoverEndpointsRequestDto dto, HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiDiscoverEndpoints(dto, currentUserId(request));
    }

    /** External_api guided wizard Step 2 — see TemplateService.aiDraftCallSkeleton's javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-draft-call-skeleton")
    public Map<String, Object> aiDraftCallSkeleton(@RequestBody AiDraftCallSkeletonRequestDto dto, HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiDraftCallSkeleton(dto, currentUserId(request));
    }

    /** External_api guided wizard Step 3 — see TemplateService.aiMapCallFields' javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-map-call-fields")
    public Map<String, Object> aiMapCallFields(@RequestBody AiMapCallFieldsRequestDto dto, HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiMapCallFields(dto, currentUserId(request));
    }

    /** FEAT-06 Phase 7 — "test this call now": runs a Designer draft (external_api or mcp_tool) live, no persistence. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/test-workflow-action")
    public com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult testWorkflowAction(
            @RequestBody com.turbotikects.turbotikectsserver.dto.WorkflowActionTestRequestDto dto) {
        return workflowActionTestService.test(dto);
    }

    /** "Verify Captures" — Response Mapping step, external_api only: re-checks JsonPaths against a response already fetched by an earlier "Test this call now" run, no new live call. See ExternalApiActionExecutor.evaluateResponseCaptures' javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/evaluate-response-captures")
    public com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult evaluateResponseCaptures(
            @RequestBody EvaluateResponseCapturesRequestDto dto) {
        return workflowActionTestService.evaluateResponseCaptures(dto.getCalls());
    }

    /** "Auto-map from this response" — re-derives responseCaptures/resultPath + fieldMappings.response for already-tested calls, grounded in their REAL captured response rather than a guess. See TemplateService.aiRefineResponseMapping's javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-refine-response-mapping")
    public Map<String, Object> aiRefineResponseMapping(@RequestBody com.turbotikects.turbotikectsserver.dto.AiRefineResponseMappingRequestDto dto, HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiRefineResponseMapping(dto, currentUserId(request));
    }

    /** "Fix/adjust with AI" — external_api Field Mapping and Test steps: ask the AI to adjust the one call's mapping/URL (an error is included when this follows a failed test, omitted otherwise), continuing the guided wizard's session. See TemplateService.aiFixCall's javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/ai-fix-call")
    public Map<String, Object> aiFixCall(@RequestBody AiFixCallRequestDto dto, HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiFixCall(dto, currentUserId(request));
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
