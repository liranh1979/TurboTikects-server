package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class McpDiscoverToolsRequestDto {
    private String serverUrl;
    // Plaintext, used only for this one live discovery call — never persisted here. The real,
    // encrypted token for actual execution gets entered separately via the normal template-save
    // path (TemplateService.encryptWorkflowSecrets), exactly like external_api's credentials.
    private String token;
}
