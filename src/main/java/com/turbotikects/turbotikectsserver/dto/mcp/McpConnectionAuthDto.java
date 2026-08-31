package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** How THIS APP connects to an external MCP server — distinct from McpTargetAuthDto, which is a
 * built-in server's wrapped target API credential. Plaintext fields are encrypted immediately in
 * McpServerService before anything is persisted, never echoed back in a response. */
@Data
public class McpConnectionAuthDto {
    /** none | bearer | api_key | basic | oauth2_client_credentials | oauth2_authorization_code */
    private String type;

    @JsonProperty("header_name")
    private String headerName;

    /** bearer / api_key. */
    private String token;

    /** basic. */
    private String username;
    private String password;

    /** Both OAuth2 types. */
    @JsonProperty("oauth2_authorize_url")
    private String oauth2AuthorizeUrl;

    @JsonProperty("oauth2_token_url")
    private String oauth2TokenUrl;

    @JsonProperty("oauth2_client_id")
    private String oauth2ClientId;

    @JsonProperty("oauth2_client_secret")
    private String oauth2ClientSecret;

    @JsonProperty("oauth2_scope")
    private String oauth2Scope;
}
