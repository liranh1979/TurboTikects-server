package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiWorkflowActionDraftRequestDto {
    private String documentation;
    private String intent;
    private List<String> ticketFieldKeys;
    // Custom field_definitions rows (entity_type='workflow', from Workflow Fields Manager), each
    // with its type — lets the AI draft request-side "this.<key>" sources (an earlier-filled/
    // captured item value) and response-side "this.<key>" targets using real, admin-defined keys
    // whose type it can reason about, and — when no existing field is a good name+type match —
    // propose creating a new one via the response's "missingWorkflowFields" array instead.
    private List<WorkflowFieldRefDto> workflowFields;
}
