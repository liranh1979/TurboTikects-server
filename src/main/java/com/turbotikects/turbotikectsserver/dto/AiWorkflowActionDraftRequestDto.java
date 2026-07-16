package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiWorkflowActionDraftRequestDto {
    private String documentation;
    private String intent;
    private List<String> ticketFieldKeys;
    // Custom field_definitions rows (entity_type='workflow', from Workflow Fields Manager) — lets
    // the AI draft request-side "this.<key>" sources (an earlier-filled/captured item value) and
    // response-side "this.<key>" targets using real, admin-defined keys instead of inventing one.
    private List<String> workflowFieldKeys;
}
