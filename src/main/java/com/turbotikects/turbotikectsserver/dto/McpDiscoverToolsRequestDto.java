package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class McpDiscoverToolsRequestDto {
    private String serverUrl;
    // "none" | "bearer" | "api_key" — mirrors external_api's auth.type vocabulary for the same
    // reason: not every real-world MCP server accepts "Authorization: Bearer", some require a
    // custom header (e.g. Google's Travel Impact Model MCP needs "X-Goog-Api-Key").
    private String type;
    // Only meaningful when type = "api_key" — defaults to "X-API-Key" if blank/omitted.
    private String headerName;
    // Plaintext, used only for this one live discovery call — never persisted here. The real,
    // encrypted token for actual execution gets entered separately via the normal template-save
    // path (TemplateService.encryptWorkflowSecrets), exactly like external_api's credentials.
    private String token;
}
