package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateNotificationTemplateDto {
    @JsonProperty("isEnabled")
    private Boolean enabled;
    private String subjectTemplate;
    private String bodyTemplate;
}
