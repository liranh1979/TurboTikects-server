package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiRefineResponseMappingRequestDto {
    private String type; // "external_api" | "mcp_tool"
    private String intent;
    // Optional — the same API documentation pasted in Step 1, if the admin still has it. Helps
    // disambiguate a generic/abbreviated real response key (e.g. "dur") whose intended meaning is
    // only clear from the docs' own field description, on top of the real captured response.
    private String documentation;
    private List<WorkflowFieldRefDto> ticketFields;
    private List<WorkflowFieldRefDto> workflowFields;
    private List<AiRefineCallInputDto> calls;
    /** external_api only — the "api_action_builder" session Steps 1-3 already used; null for mcp_tool (that branch never sends one, unchanged one-shot behavior) or when called standalone. */
    private Long sessionId;
}
