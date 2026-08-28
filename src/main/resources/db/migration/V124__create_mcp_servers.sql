-- FEAT-19 — AI-Generated MCP Servers. See V2/mcp-server-management/02-data-model.html for the
-- full design writeup. DB holds desired configuration only; runtime status (is the process alive,
-- which port, recent logs) is deliberately never persisted — it lives in the in-memory
-- McpServerRegistry and is rebuilt from this table on every boot, same reasoning as
-- TaskProgressService's in-memory task bookkeeping.
CREATE TABLE mcp_servers (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(120)  NOT NULL,
    description           VARCHAR(500),
    target_api_base_url   VARCHAR(500)  NOT NULL,
    target_api_docs       LONGTEXT,
    target_api_auth       JSON,
    tool_design_json      LONGTEXT,
    script_content        LONGTEXT,
    dependencies          TEXT,
    port                  INT           NOT NULL UNIQUE,
    ai_chat_session_id    BIGINT,
    is_enabled            TINYINT(1)    NOT NULL DEFAULT 1,
    is_system             TINYINT(1)    NOT NULL DEFAULT 0,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mcp_server_ai_chat_session FOREIGN KEY (ai_chat_session_id)
        REFERENCES ai_chat_sessions(id) ON DELETE SET NULL
);

-- Super-admin-gated (checked in-code, same as SystemSettingsController/SslSettingsController) —
-- this permission alone is necessary but not sufficient: McpServerController additionally requires
-- is_super_admin on every method, since this feature spawns real OS processes.
INSERT INTO permissions (permission_key, display_order) VALUES ('MANAGE_MCP_SERVERS', 6);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'permission_manage_mcp_servers_name', 'Manage MCP Servers',                                                     'system'),
('en', 'permission_manage_mcp_servers_desc', 'Create AI-generated built-in MCP servers and run them as local processes', 'system');
