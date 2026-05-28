package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateTranslationsRequestDto {
    private String lang;
    private Map<String, String> translations;
    private String type;
}