package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NotificationTemplateDto {
    private Long id;
    private String notificationType;
    private String displayName;
    private String description;
    @JsonProperty("isEnabled")
    private boolean enabled;
    private String subjectTemplate;
    private String bodyTemplate;
    @JsonProperty("isAdminFacing")
    private boolean adminFacing;
}
