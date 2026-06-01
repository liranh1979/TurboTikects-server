package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AzureConfigDto {

    private Long id;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("client_id")
    private String clientId;

    // write-only: sent by client, never returned in GET responses
    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("user_filter")
    private String userFilter;

    @JsonProperty("group_filter")
    private String groupFilter;

    @JsonProperty("last_synced_at")
    private LocalDateTime lastSyncedAt;

    @JsonProperty("is_active")
    private boolean isActive = false;
}
