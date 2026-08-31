package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** All fields optional/partial — null means "leave unchanged", matching McpServerUpdateDto's own
 * convention. A secret field left null keeps the previously-saved encrypted value; an empty string
 * clears it. */
@Data
public class McpExternalServerUpdateDto {
    private String name;
    private String description;

    @JsonProperty("server_url")
    private String serverUrl;

    private McpConnectionAuthDto auth;
}
