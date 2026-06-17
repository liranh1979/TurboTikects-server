package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SaveLayoutRequestDto {
    private String name;
    private String description;
    private String aiPurpose;
    private Map<String, Object> layout;
}
