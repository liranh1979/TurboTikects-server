package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SetupStatusDto {
    @JsonProperty("ai_configured")       private boolean aiConfigured;
    @JsonProperty("template_configured") private boolean templateConfigured;
    @JsonProperty("email_configured")    private boolean emailConfigured;
    @JsonProperty("users_configured")    private boolean usersConfigured;
    @JsonProperty("sso_configured")      private boolean ssoConfigured;
}
