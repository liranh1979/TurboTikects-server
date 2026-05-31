package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LdapConfigDto {

    private Long id;

    @JsonProperty("display_name")
    private String displayName;

    private String host;
    private int port = 389;

    @JsonProperty("use_ssl")
    private boolean useSsl = false;

    @JsonProperty("bind_dn")
    private String bindDn;

    // write-only: sent by client, never returned in GET responses
    @JsonProperty("bind_password")
    private String bindPassword;

    @JsonProperty("base_dn_users")
    private String baseDnUsers;

    @JsonProperty("base_dn_groups")
    private String baseDnGroups;

    @JsonProperty("user_filter")
    private String userFilter = "(objectClass=person)";

    @JsonProperty("group_filter")
    private String groupFilter = "(objectClass=group)";

    @JsonProperty("last_synced_at")
    private LocalDateTime lastSyncedAt;

    @JsonProperty("is_active")
    private boolean isActive = false;
}
