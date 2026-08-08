package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class SaveRecurringScheduleRequestDto {
    private String name;
    private Long templateId;
    private String cronExpression;
    private String frequencyType;
    private String titleTemplate;
    private Integer assignGroupId;
    private String priority;
    private Boolean isActive;
}
