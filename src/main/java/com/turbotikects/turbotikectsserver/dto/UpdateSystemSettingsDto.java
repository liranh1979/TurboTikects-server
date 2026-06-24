package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSystemSettingsDto {
    private String defaultLanguageCode;
    private String defaultTimezone;
    private String defaultTimeFormat;
}
