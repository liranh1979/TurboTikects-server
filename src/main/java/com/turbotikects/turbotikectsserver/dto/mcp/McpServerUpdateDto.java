package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** PATCH payload — every field optional/null-means-unchanged. Changing scriptContent/dependencies
 * triggers a redeploy (stop + rewrite + venv/pip + respawn). */
@Data
public class McpServerUpdateDto {
    private String name;
    private String description;

    @JsonProperty("script_content")
    private String scriptContent;

    private String dependencies;

    @JsonProperty("is_enabled")
    private Boolean enabled;
}
