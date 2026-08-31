package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** List/detail response — status/port/toolCount/lastError are merged in from the in-memory
 * McpServerRegistry at read time, not stored on the entity. Snake_case wire format, matching
 * AiSettingsDto's established convention for entity-CRUD-style DTOs. */
@Data
public class McpServerDto {
    private Long id;

    @JsonProperty("server_kind")
    private String serverKind;

    private String name;
    private String description;

    @JsonProperty("target_api_base_url")
    private String targetApiBaseUrl;

    private Integer port;

    /** external-kind only, from here down. */
    @JsonProperty("server_url")
    private String serverUrl;

    @JsonProperty("connection_auth_type")
    private String connectionAuthType;

    @JsonProperty("connection_auth_header_name")
    private String connectionAuthHeaderName;

    @JsonProperty("oauth2_client_id")
    private String oauth2ClientId;

    @JsonProperty("oauth2_authorize_url")
    private String oauth2AuthorizeUrl;

    @JsonProperty("oauth2_token_url")
    private String oauth2TokenUrl;

    @JsonProperty("oauth2_scope")
    private String oauth2Scope;

    /** Whether an oauth2_authorization_code connection has completed its interactive sign-in yet —
     * mirrors EmailMailboxDto.oauth2Authorized's "presence of a stored access token" convention. */
    @JsonProperty("oauth2_authorized")
    private boolean oauth2Authorized;

    @JsonProperty("is_enabled")
    private boolean enabled;

    @JsonProperty("is_system")
    private boolean system;

    private String status; // STOPPED | STARTING | RUNNING | ERROR

    @JsonProperty("tool_count")
    private Integer toolCount;

    @JsonProperty("last_error")
    private String lastError;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
