-- ============================================================
-- Full schema initialization (consolidated from V1-V6)
-- ============================================================

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    red_id INT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    is_super_admin TINYINT(1) DEFAULT 0,
    user_metadata JSON NULL
);

-- root / Password1  →  SHA1("root_Password1")
INSERT IGNORE INTO users (user_name, password, display_name, is_super_admin, user_metadata)
VALUES (
    'root',
    '1523802b2f67e5ffa5e6e77aa76cc941a392e428',
    'Root Admin',
    1,
    JSON_OBJECT(
        'role', JSON_OBJECT(
            'translation_key', 'user_role_admin',
            'value', 'Administrator',
            'default_label', 'Admin Role',
            'view_position', 1
        ),
        'department', JSON_OBJECT(
            'translation_key', 'dept_support',
            'value', 'General Support',
            'default_label', 'Support Team',
            'view_position', 2
        ),
        'notifications', JSON_OBJECT(
            'translation_key', 'pref_email',
            'value', 'enabled',
            'default_label', 'Email Notifications',
            'view_position', 3
        )
    )
);

-- 2. Field definitions
CREATE TABLE IF NOT EXISTS field_definitions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    field_type VARCHAR(32) DEFAULT 'string',
    is_list_visible TINYINT(1) DEFAULT 0,
    is_detail_visible TINYINT(1) DEFAULT 1,
    display_order INT DEFAULT 0,
    is_system TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY entity_field (entity_type, field_key)
);

INSERT IGNORE INTO field_definitions (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order)
VALUES
    ('user', 'role',          'string',  1, 1, 1),
    ('user', 'department',    'string',  1, 1, 2),
    ('user', 'notifications', 'boolean', 0, 1, 3);

-- 3. Dynamic translations
--    type: 'system' for core UI keys, 'user_fields' for user-defined field labels
CREATE TABLE IF NOT EXISTS dynamic_translations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    lang_code VARCHAR(5) NOT NULL,
    translation_key VARCHAR(128) NOT NULL,
    translated_text TEXT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'user_fields',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY lang_key (lang_code, translation_key)
);

-- User-field translations
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'user_role_admin', 'Administrator Role', 'user_fields'),
    ('en', 'dept_support',    'Support Department',  'user_fields'),
    ('en', 'pref_email',      'Email Notifications', 'user_fields');

-- System translations
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'login_username',           'User name',                                                              'system'),
    ('en', 'login_password',           'Password',                                                               'system'),
    ('en', 'login_submit_btn',         'Login to Portal',                                                        'system'),
    ('en', 'welcome_hello',            'Hello',                                                                   'system'),
    ('en', 'home_subtitle',            'Successfully signed in to TicketMaster',                                  'system'),
    ('en', 'logout_btn',               'Logout',                                                                  'system'),
    ('en', 'settings_tooltip',         'Open System Settings',                                                    'system'),
    ('en', 'settings_fields_manager',  'Custom Fields Manager',                                                   'system'),
    ('en', 'field_group_system',       'System Fields',                                                           'system'),
    ('en', 'field_group_custom',       'User Custom Fields',                                                      'system'),
    ('en', 'entity_users',             'Users Entity',                                                            'system'),
    ('en', 'back_btn',                 'Back',                                                                    'system'),
    ('en', 'select_entity_to_manage',  'Select Entity to Manage',                                                 'system'),
    ('en', 'manage_system_translations','Manage System Translations',                                             'system'),
    ('en', 'save_btn',                 'Save Changes',                                                            'system'),
    ('en', 'editing_language',         'Editing Language',                                                        'system'),
    ('en', 'key',                      'System Key',                                                              'system'),
    ('en', 'english_source',           'English Source',                                                          'system'),
    ('en', 'translation',              'Translation',                                                             'system'),
    ('en', 'saving',                   'Saving...',                                                               'system'),
    ('en', 'settings_ai_manager',      'AI Agent Manager',                                                        'system'),
    ('en', 'ai_agent_management',      'AI Agent Management',                                                     'system'),
    ('en', 'ai_management_desc',       'Configure your AI providers for automated translations and system intelligence.', 'system'),
    ('en', 'add_new_provider',         'Add New AI Provider',                                                     'system'),
    ('en', 'provider',                 'Provider',                                                                'system'),
    ('en', 'model',                    'Model',                                                                   'system'),
    ('en', 'status',                   'Status',                                                                  'system'),
    ('en', 'actions',                  'Actions',                                                                 'system'),
    ('en', 'set_active',               'Set Active',                                                              'system'),
    ('en', 'active',                   'Active',                                                                  'system'),
    ('en', 'save_provider',            'Save Provider',                                                           'system'),
    ('en', 'confirm_delete',           'Are you sure you want to delete this AI configuration?',                  'system');

-- 4. System languages
CREATE TABLE IF NOT EXISTS system_languages (
    code VARCHAR(5) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO system_languages (code, name) VALUES ('en', 'English');

-- 5. AI settings
CREATE TABLE IF NOT EXISTS ai_settings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL,
    api_key TEXT NOT NULL,
    base_url VARCHAR(255),
    model_name VARCHAR(100),
    is_active BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);