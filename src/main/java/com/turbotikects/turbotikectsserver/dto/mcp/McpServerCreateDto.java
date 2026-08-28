package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Step 4's "Deploy" payload — everything needed to persist and start a new McpServerEntity. */
@Data
public class McpServerCreateDto {
    private String name;
    private String description;

    @JsonProperty("target_api_base_url")
    private String targetApiBaseUrl;

    @JsonProperty("target_api_docs")
    private String targetApiDocs;

    private McpTargetAuthDto auth;

    @JsonProperty("tool_design_json")
    private String toolDesignJson;

    @JsonProperty("script_content")
    private String scriptContent;

    /** Newline-separated pip requirements, AI-proposed and admin-reviewable. */
    private String dependencies;

    @JsonProperty("ai_chat_session_id")
    private Long aiChatSessionId;
}
