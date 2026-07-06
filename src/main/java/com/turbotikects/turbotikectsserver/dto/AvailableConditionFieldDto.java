package com.turbotikects.turbotikectsserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AvailableConditionFieldDto {
    private String key;
    private String label;
    private String fieldType;
    private Boolean isCustom;
    private List<String> fieldOptions;
}
