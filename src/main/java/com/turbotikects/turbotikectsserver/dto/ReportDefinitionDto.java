package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ReportDefinitionDto {
    private Long id;
    private String name;
    private String description;
    private List<String> selectedFields;
    private Map<String, Object> conditions;
    private List<String> exportFormats;
    private Boolean isActive;
    private Boolean aiGenerated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Boolean scheduleEnabled;
    private String cronExpression;
    private String frequencyType;
    private List<Integer> recipientUserIds;
    private Integer recipientGroupId;
    private String recipientGroupName;
    private LocalDateTime nextRunAt;

    private LocalDateTime lastRunAt;
    private String lastRunStatus;
}
