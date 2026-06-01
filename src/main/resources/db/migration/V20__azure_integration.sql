-- ── 1. Extend users table ────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN azure_external_id VARCHAR(255) NULL
        COMMENT 'Azure AD object ID (GUID) for source_type=2 users';

-- ── 2. Azure AD configurations ───────────────────────────────────────────────
CREATE TABLE azure_configs (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    display_name     VARCHAR(128) NOT NULL,
    tenant_id        VARCHAR(255) NOT NULL,
    client_id        VARCHAR(255) NOT NULL,
    client_secret    TEXT         NOT NULL COMMENT 'AES-256 encrypted',
    user_filter      VARCHAR(512) NULL     COMMENT 'OData $filter for /users, e.g. accountEnabled eq true',
    group_filter     VARCHAR(512) NULL     COMMENT 'OData $filter for /groups',
    user_delta_link  TEXT         NULL     COMMENT 'Graph delta link for incremental user sync',
    group_delta_link TEXT         NULL     COMMENT 'Graph delta link for incremental group sync',
    last_synced_at   DATETIME     NULL,
    is_active        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── 3. Field mappings (Graph attribute → system field_key) ───────────────────
CREATE TABLE azure_field_mappings (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    azure_config_id  INT          NOT NULL,
    entity_type      VARCHAR(16)  NOT NULL COMMENT 'user or group',
    azure_attribute  VARCHAR(128) NOT NULL COMMENT 'Graph API property name, e.g. userPrincipalName',
    system_field_key VARCHAR(128) NOT NULL COMMENT 'field_key from field_definitions, or username/display_name',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_azure_mapping (azure_config_id, entity_type, azure_attribute),
    FOREIGN KEY (azure_config_id) REFERENCES azure_configs(id) ON DELETE CASCADE
);

-- ── 4. New permission ─────────────────────────────────────────────────────────
INSERT INTO permissions (permission_key, display_order) VALUES ('MANAGE_AZURE', 7);

-- ── 5. Translation keys ───────────────────────────────────────────────────────
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
-- Permission
('en', 'permission_manage_azure_name',        'Manage Azure AD',                                                                                                                          'system'),
('en', 'permission_manage_azure_desc',        'Configure Azure AD tenants and field mappings',                                                                                            'system'),
-- Settings card
('en', 'settings_azure_manager',              'Azure AD / Microsoft Entra',                                                                                                               'system'),
-- Config list page
('en', 'azure_configs_title',                 'Azure AD Configurations',                                                                                                                  'system'),
('en', 'azure_new_config_btn',                'New Azure AD Connection',                                                                                                                   'system'),
('en', 'azure_no_configs',                    'No Azure AD connections configured yet',                                                                                                    'system'),
('en', 'azure_col_name',                      'Name',                                                                                                                                     'system'),
('en', 'azure_col_tenant',                    'Tenant',                                                                                                                                    'system'),
('en', 'azure_col_status',                    'Status',                                                                                                                                    'system'),
('en', 'azure_col_last_synced',               'Last Synced',                                                                                                                               'system'),
('en', 'azure_status_active',                 'Active',                                                                                                                                    'system'),
('en', 'azure_status_inactive',               'Inactive',                                                                                                                                  'system'),
('en', 'azure_never_synced',                  'Never',                                                                                                                                     'system'),
('en', 'azure_delete_confirm',                'Delete this Azure AD configuration? Synced users will become manual users.',                                                                 'system'),
-- Wizard steps
('en', 'azure_wizard_title_new',              'New Azure AD Connection',                                                                                                                   'system'),
('en', 'azure_wizard_title_edit',             'Edit Azure AD Connection',                                                                                                                  'system'),
('en', 'azure_step_connection',               'Connection',                                                                                                                                'system'),
('en', 'azure_step_sample',                   'Sample Data',                                                                                                                               'system'),
('en', 'azure_step_mapping',                  'Field Mapping',                                                                                                                             'system'),
('en', 'azure_step_confirm',                  'Confirm',                                                                                                                                   'system'),
-- Step 1 — connection fields
('en', 'azure_field_display_name',            'Connection Name',                                                                                                                           'system'),
('en', 'azure_field_tenant_id',               'Tenant ID',                                                                                                                                 'system'),
('en', 'azure_field_tenant_id_hint',          'Your Azure AD directory (tenant) ID or primary domain',                                                                                    'system'),
('en', 'azure_field_client_id',               'Client ID',                                                                                                                                 'system'),
('en', 'azure_field_client_id_hint',          'Application (client) ID from your App Registration',                                                                                       'system'),
('en', 'azure_field_client_secret',           'Client Secret',                                                                                                                             'system'),
('en', 'azure_field_user_filter',             'User Filter (optional)',                                                                                                                    'system'),
('en', 'azure_field_user_filter_hint',        'OData $filter expression, e.g. accountEnabled eq true',                                                                                    'system'),
('en', 'azure_field_group_filter',            'Group Filter (optional)',                                                                                                                   'system'),
('en', 'azure_field_group_filter_hint',       'OData $filter expression for groups',                                                                                                      'system'),
('en', 'azure_test_connection_btn',           'Test Connection',                                                                                                                           'system'),
('en', 'azure_connection_success',            'Connection successful',                                                                                                                     'system'),
('en', 'azure_connection_failed',             'Connection failed',                                                                                                                         'system'),
-- Step 2 — sample data
('en', 'azure_step2_intro',                   'Fetch a sample user and group from your Azure AD tenant to preview the available attributes.',                                              'system'),
('en', 'azure_fetch_sample_btn',              'Fetch Sample User',                                                                                                                         'system'),
('en', 'azure_fetch_sample_group_btn',        'Fetch Sample Group',                                                                                                                        'system'),
('en', 'azure_sample_user_title',             'Sample Azure AD User',                                                                                                                      'system'),
('en', 'azure_sample_group_title',            'Sample Azure AD Group',                                                                                                                     'system'),
('en', 'azure_no_sample_yet',                 'No sample fetched yet',                                                                                                                     'system'),
-- Step 3 — AI mapping
('en', 'azure_step3_intro',                   'The AI will map Azure AD attributes to system fields. Review and adjust the suggested mappings below.',                                     'system'),
('en', 'azure_mapping_title',                 'AI Field Mapping',                                                                                                                          'system'),
('en', 'azure_mapping_running',               'Asking AI to map fields...',                                                                                                                'system'),
('en', 'azure_mapping_azure_attr',            'Azure Attribute',                                                                                                                           'system'),
('en', 'azure_mapping_system_field',          'System Field',                                                                                                                              'system'),
('en', 'azure_mapping_confidence',            'Confidence',                                                                                                                                'system'),
('en', 'azure_missing_fields_title',          'Missing Fields',                                                                                                                            'system'),
('en', 'azure_missing_fields_desc',           'The AI found Azure attributes with no matching system field. Create the suggested fields below, then return here to complete mapping.',    'system'),
('en', 'azure_go_create_fields_btn',          'Create Suggested Fields',                                                                                                                   'system'),
('en', 'azure_remap_btn',                     'Re-run AI Mapping',                                                                                                                         'system'),
-- Step 4 — confirm
('en', 'azure_step4_intro',                   'Review your Azure AD configuration before activating.',                                                                                    'system'),
('en', 'azure_step4_ready',                   'Your Azure AD connection is ready.',                                                                                                        'system'),
('en', 'azure_sync_option_title',             'Run initial sync now?',                                                                                                                     'system'),
('en', 'azure_sync_option_desc',              'Import users and groups from Azure AD immediately after saving.',                                                                           'system'),
('en', 'azure_save_config_btn',               'Save & Activate',                                                                                                                           'system'),
('en', 'azure_save_draft_btn',                'Save as Draft',                                                                                                                             'system'),
('en', 'azure_config_saved',                  'Azure AD configuration saved',                                                                                                              'system'),
-- Sync
('en', 'azure_sync_now_btn',                  'Sync Now',                                                                                                                                  'system'),
('en', 'azure_sync_task_name',                'Azure AD Sync',                                                                                                                             'system');
