package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SaveReportRequestDto {
    private String name;
    private String description;
    private List<String> selectedFields;
    private Map<String, Object> conditions;
    private List<String> exportFormats;
    private Boolean isActive;
    private Boolean aiGenerated;
    private String lastAiPrompt;

    // Schedule — all fields below are ignored (and any existing schedule removed) when
    // scheduleEnabled is not true. Exactly one of recipientUserIds / recipientGroupId is set.
    private Boolean scheduleEnabled;
    private String cronExpression;
    private String frequencyType;
    private List<Integer> recipientUserIds;
    private Integer recipientGroupId;
}
