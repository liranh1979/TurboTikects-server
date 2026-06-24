package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SelfProfileFieldDto {
    private String fieldKey;
    private String fieldType;
    private List<String> fieldOptions;
    private String value;
    private boolean editable;
}
