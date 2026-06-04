package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TemplateWithLayoutDto {
    private Long id;
    private String name;
    private String description;
    private int currentVersionNumber;
    private Long currentVersionId;
    private Map<String, Object> layout;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
