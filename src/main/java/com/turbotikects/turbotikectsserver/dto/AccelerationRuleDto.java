package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AccelerationRuleDto {
    private Long id;
    private String name;
    private String description;
    private Integer executionOrder;
    private String triggerConditions;
    private String actions;
    private String generatedScript;
    private Boolean isActive;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
