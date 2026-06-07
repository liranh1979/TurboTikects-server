package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TicketAiAnalyzeResponseDto {
    private Long matchedTemplateId;
    private String matchedTemplateName;
    private int confidence;
    private Map<String, Object> suggestedFields;
    private List<Long> suggestedLabelIds;
    private List<String> suggestedLabelKeys;
    private String reasoning;
}
