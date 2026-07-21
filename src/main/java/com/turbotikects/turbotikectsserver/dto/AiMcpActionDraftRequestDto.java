package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiMcpActionDraftRequestDto {
    private String intent;
    // The server's REAL discovered tools (McpController.discoverTools' output, round-tripped back
    // in) — {name, description, inputSchema} per tool. Ground truth, not guessed, which is the
    // whole advantage of drafting against MCP over the freeform-HTTP-docs external_api case.
    private List<Map<String, Object>> tools;
    private List<String> ticketFieldKeys;
    // Custom field_definitions rows (entity_type='workflow', from Workflow Fields Manager) — same
    // purpose and shape as in AiWorkflowActionDraftRequestDto: real keys (with type) for
    // "this.<key>" input/output, plus the same missing-field-suggestion mechanism.
    private List<WorkflowFieldRefDto> workflowFields;
}
