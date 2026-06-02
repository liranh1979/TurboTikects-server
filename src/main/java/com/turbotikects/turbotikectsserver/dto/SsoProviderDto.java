package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SsoProviderDto(
        Long id,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("sso_display_name") String ssoDisplayName,
        String type
) {}
