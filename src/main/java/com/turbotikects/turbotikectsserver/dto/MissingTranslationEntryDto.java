package com.turbotikects.turbotikectsserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MissingTranslationEntryDto {
    private String type;
    private String key;
    private String englishText;
}
