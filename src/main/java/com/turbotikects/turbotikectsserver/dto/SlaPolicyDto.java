package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class SlaPolicyDto {
    private Long id;
    private String priority;
    private Long templateId;
    private Integer firstResponseMinutes;
    private Integer resolutionMinutes;
    private boolean businessHours;
    private Boolean isActive;
    private List<SlaEscalationStepDto> steps;
}
