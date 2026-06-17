package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TemplateWithLayoutDto {
    private Long id;
    private String name;
    private String description;
    private String aiPurpose;
    private int currentVersionNumber;
    private Long currentVersionId;
    private Map<String, Object> layout;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
