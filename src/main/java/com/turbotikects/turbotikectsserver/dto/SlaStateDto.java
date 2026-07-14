package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SlaStateDto {
    private Long slaPolicyId;
    private boolean businessHours;

    private LocalDateTime firstResponseTargetAt;
    private LocalDateTime firstResponseAt;
    private boolean firstResponseBreached;
    private Double firstResponsePercentUsed; // null once responded — frontend shows "✓ Done" instead

    private LocalDateTime resolutionTargetAt;
    private LocalDateTime resolutionAt;
    private boolean resolutionBreached;
    private Double resolutionPercentUsed;

    private LocalDateTime pausedAt;

    private Integer aiBreachRiskScore;
    private String aiRiskReason;
}
