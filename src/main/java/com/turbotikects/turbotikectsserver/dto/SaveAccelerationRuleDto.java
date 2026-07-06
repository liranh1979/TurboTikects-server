package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveAccelerationRuleDto {
    private String name;
    private String description;
    private Integer executionOrder;
    private String triggerConditions;
    private String actions;
    private String generatedScript;
    private Boolean isActive;
}
