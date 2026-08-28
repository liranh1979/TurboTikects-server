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
    private String name;
    private String description;

    @JsonProperty("target_api_base_url")
    private String targetApiBaseUrl;

    private Integer port;

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
