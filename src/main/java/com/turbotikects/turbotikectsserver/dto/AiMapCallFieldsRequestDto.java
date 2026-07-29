package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Step 3 of the external_api wizard's guided AI flow — see TemplateService.aiMapCallFields'
 * javadoc.
 */
@Data
public class AiMapCallFieldsRequestDto {
    private Long sessionId;
    private String documentation;
    private String intent;
    /** From Step 2's aiDraftCallSkeleton response — each {placeholder, description, required, example}. */
    private List<Map<String, Object>> requiredInputs;
    private List<WorkflowFieldRefDto> ticketFields;
    private List<WorkflowFieldRefDto> workflowFields;
}
