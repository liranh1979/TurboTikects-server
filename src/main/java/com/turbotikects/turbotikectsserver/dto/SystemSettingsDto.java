package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemSettingsDto {
    private String defaultLanguageCode;
    private String defaultTimezone;
    private String defaultTimeFormat;
    private String logoUrl;
    private Integer accelerationCronInterval;
}
