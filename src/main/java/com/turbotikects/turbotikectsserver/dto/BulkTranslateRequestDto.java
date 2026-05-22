package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class BulkTranslateRequestDto {
    private String targetLanguage;
    private Map<String, String> translations;
}