package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

/**
 * "Verify Captures"/"Verify Mapping" — Response Mapping step, both external_api and mcp_tool.
 * Reuses AiRefineCallInputDto's exact shape (id, name, existingResponseCaptures, rawResponse)
 * since it's already exactly what's needed here too: the call id, its current (AI-proposed,
 * tree-click-added, or admin-edited) responseCaptures to evaluate, and the real response already
 * captured by an earlier "Test this call now" run. See ExternalApiActionExecutor/
 * McpActionExecutor.evaluateResponseCaptures' javadocs.
 */
@Data
public class EvaluateResponseCapturesRequestDto {
    // "external_api" | "mcp_tool" — missing/null defaults to "external_api" in
    // WorkflowActionTestService so every caller built before mcp_tool support existed keeps
    // working unchanged.
    private String type;
    private List<AiRefineCallInputDto> calls;
}
