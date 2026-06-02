-- Add SSO login columns to azure_configs
ALTER TABLE azure_configs
    ADD COLUMN sso_enabled      TINYINT(1)   NOT NULL DEFAULT 0,
    ADD COLUMN sso_display_name VARCHAR(128) NULL COMMENT 'Label shown on the login screen button';

-- Translation keys
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'settings_external_repos',  'External Repositories',                          'system'),
('en', 'external_repos_desc',      'Azure AD and other identity providers',           'system'),
('en', 'azure_sso_col',            'SSO Login',                                       'system'),
('en', 'azure_sso_enabled',        'SSO Enabled',                                     'system'),
('en', 'azure_sso_disabled',       'SSO Disabled',                                    'system'),
('en', 'azure_sso_setup_btn',      'Setup SSO',                                       'system'),
('en', 'sso_wizard_title',         'Configure SSO Login',                             'system'),
('en', 'sso_display_name_label',   'Login Button Label',                              'system'),
('en', 'sso_display_name_hint',    'Shown on the login screen (e.g. Acme Azure AD)',  'system'),
('en', 'sso_enable_toggle',        'Enable SSO Login',                                'system'),
('en', 'sso_save_btn',             'Save SSO Settings',                               'system'),
('en', 'login_choose_provider',    'Sign in to TicketMaster',                         'system'),
('en', 'login_manual_btn',         'Sign in with username & password',                'system'),
('en', 'login_with_provider',      'Sign in with {{name}}',                           'system'),
('en', 'login_back_to_providers',  '← Back to login options',                         'system'),
('en', 'or_label',                 'or',                                                'system');
