package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FieldDefinitionDto {
    private String entityType;
    private String fieldKey;
    private String fieldType;
    private String label;
}