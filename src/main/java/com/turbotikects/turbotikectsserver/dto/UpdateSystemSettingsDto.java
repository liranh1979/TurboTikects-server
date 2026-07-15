package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateSystemSettingsDto {
    private String defaultLanguageCode;
    private String defaultTimezone;
    private String defaultTimeFormat;
    private Integer accelerationCronInterval;
    private List<String> dashboardSectionOrder;
}
