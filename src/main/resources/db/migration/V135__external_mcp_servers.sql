-- External (remote, non-built-in) MCP servers — a second "kind" of row in the existing mcp_servers
-- table alongside the FEAT-19 AI-generated/built-in kind. An external row never spawns a local
-- process (server_url points at a real remote server instead), and needs its own connection auth —
-- a different concept from target_api_auth (the *wrapped target API's* credential, built-in-only).
-- Supports the full auth spectrum a real public MCP server can require, up to and including OAuth2
-- (both the client-credentials grant for unattended calls and the interactive authorization-code
-- grant for user-delegated providers), generalizing this app's two existing OAuth2 implementations
-- (Email Integration's Gmail/Microsoft authorization-code flow; Azure AD's client-credentials flow)
-- to admin-supplied endpoints instead of a fixed provider list.

ALTER TABLE mcp_servers
    MODIFY COLUMN target_api_base_url VARCHAR(500) NULL,
    MODIFY COLUMN port INT NULL,
    ADD COLUMN server_kind ENUM('generated','external') NOT NULL DEFAULT 'generated' AFTER id,
    ADD COLUMN server_url VARCHAR(500) NULL AFTER target_api_base_url,
    ADD COLUMN connection_auth_type ENUM('none','bearer','api_key','basic','oauth2_client_credentials','oauth2_authorization_code') NULL,
    ADD COLUMN connection_auth_header_name VARCHAR(100) NULL,
    ADD COLUMN connection_auth_token_enc TEXT NULL,
    ADD COLUMN connection_auth_username_enc TEXT NULL,
    ADD COLUMN connection_auth_password_enc TEXT NULL,
    ADD COLUMN oauth2_authorize_url VARCHAR(500) NULL,
    ADD COLUMN oauth2_token_url VARCHAR(500) NULL,
    ADD COLUMN oauth2_client_id VARCHAR(255) NULL,
    ADD COLUMN oauth2_client_secret_enc TEXT NULL,
    ADD COLUMN oauth2_scope VARCHAR(500) NULL,
    ADD COLUMN oauth2_access_token_enc TEXT NULL,
    ADD COLUMN oauth2_refresh_token_enc TEXT NULL,
    ADD COLUMN oauth2_token_expiry TIMESTAMP NULL;

-- External-server management is deliberately gated on MANAGE_FIELDS only (no super-admin
-- requirement) — unlike built-in servers, an external row never spawns an OS process, so it's the
-- same trust level as building the templates/workflows that will call it. See McpServerController.

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'mcp_server_kind_generated',            'Built-in',                                   'system'),
('en', 'mcp_server_kind_external',             'External',                                   'system'),
('en', 'mcp_mode_saved',                       'Saved Server',                                'system'),
('en', 'mcp_col_kind',                         'Kind',                                        'system'),
('en', 'mcp_external_test_ok',                 'Connected — tools discovered successfully.',  'system'),
('en', 'mcp_external_token_label',             'Token',                                       'system'),
('en', 'mcp_external_name_required',           'Name is required',                            'system'),
('en', 'mcp_external_url_required',            'Server URL is required',                      'system'),
('en', 'mcp_external_save_before_test',        'Save the server first, then test the connection.', 'system'),
('en', 'mcp_external_save_before_authorize',   'Save the server first (with a token URL and client ID), then click Authorize.', 'system'),
('en', 'mcp_new_server_choice_title',          'New MCP Server',                              'system'),
('en', 'mcp_new_server_generate_option',       'AI-Generate (Built-in)',                      'system'),
('en', 'mcp_new_server_generate_option_desc',  'Describe a target API — the AI writes and runs a wrapper server for you.', 'system'),
('en', 'mcp_new_server_external_option',       'Connect External Server',                     'system'),
('en', 'mcp_new_server_external_option_desc',  'Register an existing remote MCP server you already have a URL for.',      'system'),
('en', 'mcp_external_form_title',              'Connect External MCP Server',                 'system'),
('en', 'mcp_external_form_title_edit',         'Edit External MCP Server',                    'system'),
('en', 'mcp_external_name_label',              'Name',                                        'system'),
('en', 'mcp_external_description_label',       'Description',                                 'system'),
('en', 'mcp_external_server_url_label',        'Server URL',                                  'system'),
('en', 'mcp_external_auth_type_label',         'Authentication',                              'system'),
('en', 'mcp_auth_type_none',                   'None',                                        'system'),
('en', 'mcp_auth_type_bearer',                 'Bearer token',                                'system'),
('en', 'mcp_auth_type_api_key',                'API key header',                              'system'),
('en', 'mcp_auth_type_basic',                  'Basic (username/password)',                   'system'),
('en', 'mcp_auth_type_oauth2_client_credentials', 'OAuth2 — Client Credentials (unattended)', 'system'),
('en', 'mcp_auth_type_oauth2_authorization_code', 'OAuth2 — Sign in (user-delegated)',        'system'),
('en', 'mcp_external_header_name_label',       'Header name (default X-API-Key)',             'system'),
('en', 'mcp_external_username_label',          'Username',                                    'system'),
('en', 'mcp_external_password_label',          'Password',                                    'system'),
('en', 'mcp_external_oauth2_authorize_url_label', 'Authorize URL',                            'system'),
('en', 'mcp_external_oauth2_token_url_label',  'Token URL',                                    'system'),
('en', 'mcp_external_oauth2_client_id_label',  'Client ID',                                    'system'),
('en', 'mcp_external_oauth2_client_secret_label', 'Client secret',                             'system'),
('en', 'mcp_external_oauth2_scope_label',      'Scope',                                        'system'),
('en', 'mcp_external_oauth2_authorize_btn',    'Authorize',                                    'system'),
('en', 'mcp_external_oauth2_authorized_label', 'Authorized',                                   'system'),
('en', 'mcp_external_oauth2_not_authorized_label', 'Not authorized yet — click Authorize',     'system'),
('en', 'mcp_external_test_btn',                'Test Connection',                              'system'),
('en', 'mcp_external_save_btn',                'Save Server',                                  'system');
