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
    // Ticket field_definitions rows, each with its type — lets the AI match a ticket field by type
    // fit, not just name.
    private List<WorkflowFieldRefDto> ticketFields;
    // Custom field_definitions rows (entity_type='workflow', from Workflow Fields Manager) — real
    // keys (with type) for "this.<key>" input/output, plus the missing-field-suggestion mechanism
    // (see AiMapCallFieldsRequestDto/TemplateService.aiMapCallFields for the external_api sibling).
    private List<WorkflowFieldRefDto> workflowFields;
}
