package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Registers an existing remote MCP server as a saved, reusable connection — no AI generation, no
 * local process. */
@Data
public class McpExternalServerCreateDto {
    private String name;
    private String description;

    @JsonProperty("server_url")
    private String serverUrl;

    private McpConnectionAuthDto auth;
}
