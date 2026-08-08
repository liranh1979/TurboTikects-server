package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecurringScheduleDto {
    private Long id;
    private String name;
    private Long templateId;
    private String templateName;
    private String cronExpression;
    private String frequencyType;
    private String titleTemplate;
    private Integer assignGroupId;
    private String assignGroupName;
    private String priority;
    private Boolean isActive;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
}
