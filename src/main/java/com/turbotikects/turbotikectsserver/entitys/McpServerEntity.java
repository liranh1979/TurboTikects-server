package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** FEAT-19 — desired configuration for an AI-generated, locally-run MCP server. Runtime status
 * (alive/port/logs) is never persisted here — see McpServerRegistry. */
@Entity
@Table(name = "mcp_servers")
@Data
public class McpServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "generated" (default, existing FEAT-19 rows) = AI-generated, locally-run process. "external"
     * = an admin-registered real remote MCP server — never spawns a process, uses server_url +
     * connection_auth_* instead of port/target_api_*. */
    @Column(name = "server_kind", nullable = false)
    private String serverKind = "generated";

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "target_api_base_url")
    private String targetApiBaseUrl;

    /** external-kind only — the real remote MCP server's own endpoint. */
    @Column(name = "server_url")
    private String serverUrl;

    /** external-kind only — how THIS APP connects to that server (distinct from target_api_auth,
     * which is the wrapped target API's credential for a generated/built-in server). One of
     * none/bearer/api_key/basic/oauth2_client_credentials/oauth2_authorization_code. */
    @Column(name = "connection_auth_type")
    private String connectionAuthType;

    @Column(name = "connection_auth_header_name")
    private String connectionAuthHeaderName;

    @Column(name = "connection_auth_token_enc", columnDefinition = "TEXT")
    private String connectionAuthTokenEnc;

    @Column(name = "connection_auth_username_enc", columnDefinition = "TEXT")
    private String connectionAuthUsernameEnc;

    @Column(name = "connection_auth_password_enc", columnDefinition = "TEXT")
    private String connectionAuthPasswordEnc;

    /** oauth2_authorization_code only. */
    @Column(name = "oauth2_authorize_url")
    private String oauth2AuthorizeUrl;

    /** Both OAuth2 types. */
    @Column(name = "oauth2_token_url")
    private String oauth2TokenUrl;

    @Column(name = "oauth2_client_id")
    private String oauth2ClientId;

    @Column(name = "oauth2_client_secret_enc", columnDefinition = "TEXT")
    private String oauth2ClientSecretEnc;

    @Column(name = "oauth2_scope")
    private String oauth2Scope;

    @Column(name = "oauth2_access_token_enc", columnDefinition = "TEXT")
    private String oauth2AccessTokenEnc;

    @Column(name = "oauth2_refresh_token_enc", columnDefinition = "TEXT")
    private String oauth2RefreshTokenEnc;

    @Column(name = "oauth2_token_expiry")
    private LocalDateTime oauth2TokenExpiry;

    @Column(name = "target_api_docs", columnDefinition = "LONGTEXT")
    private String targetApiDocs;

    /** JSON: {type: none|api_key|bearer, location: header|query|body, name, tokenEnc}. The wrapped
     * target API's own credential, AES-encrypted at rest — distinct from the existing mcp_tool
     * auth config, which is header-only and authenticates this app's connection TO an MCP server. */
    @Column(name = "target_api_auth", columnDefinition = "JSON")
    private String targetApiAuth;

    /** The AI's approved tool-design proposal (Step 2), kept so a script can be regenerated
     * without re-running the design call. */
    @Column(name = "tool_design_json", columnDefinition = "LONGTEXT")
    private String toolDesignJson;

    @Column(name = "script_content", columnDefinition = "LONGTEXT")
    private String scriptContent;

    /** Newline-separated pip requirements, AI-proposed and admin-reviewable. */
    @Column(columnDefinition = "TEXT")
    private String dependencies;

    /** generated-kind only — null for external servers. */
    @Column(unique = true)
    private Integer port;

    @Column(name = "ai_chat_session_id")
    private Long aiChatSessionId;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
