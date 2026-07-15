package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowActionTestRequestDto {
    // Optional — when set alongside nodeId, a saved-but-untouched secret (no plaintext in
    // typeConfig, only the current in-memory draft) can be tested by falling back to the currently
    // persisted encrypted credential for that exact node, instead of requiring the admin to retype
    // a token they already saved earlier just to run a test.
    private Long templateId;
    private String nodeId;
    private String type; // "external_api" | "mcp_tool"
    private Map<String, Object> typeConfig; // the Designer's current in-memory draft for this node
    private Map<String, String> sampleTicketFields;
}
