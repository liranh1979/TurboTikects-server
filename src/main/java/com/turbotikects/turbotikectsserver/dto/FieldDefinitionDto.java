package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class FieldDefinitionDto {
    private String entityType;
    private String fieldKey;
    private String fieldType;
    private String label;
    private List<String> fieldOptions;
    private Map<String, Object> fieldConfig;
}