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

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "target_api_base_url", nullable = false)
    private String targetApiBaseUrl;

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

    @Column(nullable = false, unique = true)
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
