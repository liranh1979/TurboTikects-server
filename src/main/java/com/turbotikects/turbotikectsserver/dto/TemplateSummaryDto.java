package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateSummaryDto {
    private Long id;
    private String name;
    private String description;
    private int currentVersionNumber;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
