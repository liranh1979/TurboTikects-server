-- ── 1. Extend users table ────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN source_type     TINYINT(1)   NOT NULL DEFAULT 0
        COMMENT '0=manual, 1=LDAP',
    ADD COLUMN ldap_external_id VARCHAR(255) NULL
        COMMENT 'DN or unique attribute from LDAP (used for delta sync matching)';

-- ── 2. LDAP server configurations ────────────────────────────────────────────
CREATE TABLE ldap_configs (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    display_name   VARCHAR(128) NOT NULL,
    host           VARCHAR(255) NOT NULL,
    port           INT          NOT NULL DEFAULT 389,
    use_ssl        TINYINT(1)   NOT NULL DEFAULT 0,
    bind_dn        VARCHAR(255) NOT NULL,
    bind_password  TEXT         NOT NULL  COMMENT 'AES-256 encrypted',
    base_dn_users  VARCHAR(255) NOT NULL,
    base_dn_groups VARCHAR(255) NULL,
    user_filter    VARCHAR(255) NOT NULL DEFAULT '(objectClass=person)',
    group_filter   VARCHAR(255) NOT NULL DEFAULT '(objectClass=group)',
    last_synced_at DATETIME     NULL,
    is_active      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ── 3. Field mappings (LDAP attribute → system field_key) ────────────────────
CREATE TABLE ldap_field_mappings (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    ldap_config_id   INT          NOT NULL,
    entity_type      VARCHAR(16)  NOT NULL  COMMENT 'user or group',
    ldap_attribute   VARCHAR(128) NOT NULL  COMMENT 'LDAP attribute name, e.g. sAMAccountName',
    system_field_key VARCHAR(128) NOT NULL  COMMENT 'field_key from field_definitions, or username/display_name',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_mapping (ldap_config_id, entity_type, ldap_attribute),
    FOREIGN KEY (ldap_config_id) REFERENCES ldap_configs(id) ON DELETE CASCADE
);

-- ── 4. New permission ─────────────────────────────────────────────────────────
INSERT INTO permissions (permission_key, display_order) VALUES ('MANAGE_LDAP', 6);

-- ── 5. Translation keys ───────────────────────────────────────────────────────
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
-- Permission
('en', 'permission_manage_ldap_name',     'Manage LDAP',                                                                                                               'system'),
('en', 'permission_manage_ldap_desc',     'Configure LDAP servers and field mappings',                                                                                  'system'),
-- Settings card
('en', 'settings_ldap_manager',           'LDAP / Directory Sync',                                                                                                     'system'),
-- LDAP config list page
('en', 'ldap_configs_title',              'LDAP Configurations',                                                                                                        'system'),
('en', 'ldap_new_config_btn',             'New LDAP Connection',                                                                                                        'system'),
('en', 'ldap_no_configs',                 'No LDAP connections configured yet',                                                                                         'system'),
('en', 'ldap_col_name',                   'Name',                                                                                                                       'system'),
('en', 'ldap_col_host',                   'Host',                                                                                                                       'system'),
('en', 'ldap_col_status',                 'Status',                                                                                                                     'system'),
('en', 'ldap_col_last_synced',            'Last Synced',                                                                                                                'system'),
('en', 'ldap_status_active',              'Active',                                                                                                                     'system'),
('en', 'ldap_status_inactive',            'Inactive',                                                                                                                   'system'),
('en', 'ldap_never_synced',               'Never',                                                                                                                      'system'),
-- Wizard step 1 — connection
('en', 'ldap_wizard_title_new',           'New LDAP Connection',                                                                                                        'system'),
('en', 'ldap_wizard_title_edit',          'Edit LDAP Connection',                                                                                                       'system'),
('en', 'ldap_step_connection',            'Connection',                                                                                                                 'system'),
('en', 'ldap_step_sample',                'Sample Data',                                                                                                                'system'),
('en', 'ldap_step_mapping',               'Field Mapping',                                                                                                              'system'),
('en', 'ldap_step_confirm',               'Confirm',                                                                                                                    'system'),
('en', 'ldap_field_display_name',         'Connection Name',                                                                                                            'system'),
('en', 'ldap_field_host',                 'LDAP Host',                                                                                                                  'system'),
('en', 'ldap_field_port',                 'Port',                                                                                                                       'system'),
('en', 'ldap_field_use_ssl',              'Use SSL / LDAPS',                                                                                                            'system'),
('en', 'ldap_field_bind_dn',              'Bind DN',                                                                                                                    'system'),
('en', 'ldap_field_bind_password',        'Bind Password',                                                                                                              'system'),
('en', 'ldap_field_base_dn_users',        'User Base DN',                                                                                                               'system'),
('en', 'ldap_field_base_dn_groups',       'Group Base DN (optional)',                                                                                                   'system'),
('en', 'ldap_field_user_filter',          'User Filter',                                                                                                                'system'),
('en', 'ldap_field_group_filter',         'Group Filter',                                                                                                               'system'),
('en', 'ldap_test_connection_btn',        'Test Connection',                                                                                                            'system'),
('en', 'ldap_connection_success',         'Connection successful',                                                                                                      'system'),
('en', 'ldap_connection_failed',          'Connection failed',                                                                                                          'system'),
-- Wizard step 2 — sample
('en', 'ldap_fetch_sample_btn',           'Fetch Sample User',                                                                                                          'system'),
('en', 'ldap_fetch_sample_group_btn',     'Fetch Sample Group',                                                                                                         'system'),
('en', 'ldap_sample_user_title',          'Sample LDAP User',                                                                                                           'system'),
('en', 'ldap_sample_group_title',         'Sample LDAP Group',                                                                                                          'system'),
('en', 'ldap_no_sample_yet',              'No sample fetched yet',                                                                                                      'system'),
-- Wizard step 3 — mapping
('en', 'ldap_mapping_title',              'AI Field Mapping',                                                                                                           'system'),
('en', 'ldap_mapping_running',            'Asking AI to map fields...',                                                                                                 'system'),
('en', 'ldap_mapping_ldap_attr',          'LDAP Attribute',                                                                                                             'system'),
('en', 'ldap_mapping_system_field',       'System Field',                                                                                                               'system'),
('en', 'ldap_mapping_confidence',         'Confidence',                                                                                                                 'system'),
('en', 'ldap_missing_fields_title',       'Missing Fields',                                                                                                             'system'),
('en', 'ldap_missing_fields_desc',        'The AI found LDAP attributes with no matching system field. Create the suggested fields below, then return here to complete mapping.', 'system'),
('en', 'ldap_go_create_fields_btn',       'Create Suggested Fields',                                                                                                    'system'),
('en', 'ldap_remap_btn',                  'Re-run AI Mapping',                                                                                                          'system'),
-- Wizard step 4 — confirm
('en', 'ldap_save_config_btn',            'Save & Activate',                                                                                                            'system'),
('en', 'ldap_save_draft_btn',             'Save as Draft',                                                                                                              'system'),
('en', 'ldap_config_saved',               'LDAP configuration saved',                                                                                                   'system'),
-- Sync
('en', 'ldap_sync_now_btn',               'Sync Now',                                                                                                                   'system'),
('en', 'ldap_sync_task_name',             'LDAP Sync',                                                                                                                  'system'),
('en', 'ldap_delete_confirm',             'Delete this LDAP configuration? Synced users will become manual users.',                                                      'system');
