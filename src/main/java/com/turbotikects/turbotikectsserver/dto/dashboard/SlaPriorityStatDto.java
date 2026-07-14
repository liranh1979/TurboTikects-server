package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlaPriorityStatDto {
    private String priority;
    private long total;
    private long resolvedCount;
    private long firstResponseBreached;
    private long resolutionBreached;
    private Double resolutionBreachRatePercent;
}
