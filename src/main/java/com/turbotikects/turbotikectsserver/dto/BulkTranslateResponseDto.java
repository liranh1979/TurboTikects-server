package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class BulkTranslateResponseDto {
    private boolean success;
    private Map<String, String> translations;
}