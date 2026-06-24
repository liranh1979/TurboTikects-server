-- System Settings: a single global row of org-wide defaults (super-admin only)
CREATE TABLE system_settings (
    id                     TINYINT      NOT NULL DEFAULT 1 PRIMARY KEY,
    default_language_code  VARCHAR(5)   NOT NULL DEFAULT 'en',
    default_timezone       VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    default_time_format    VARCHAR(3)   NOT NULL DEFAULT '24h',
    logo_path              VARCHAR(255) NULL,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_settings (id) VALUES (1);

-- Translation keys (English only, per project convention)
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'system_settings_card_label',     'System Settings',                                            'system'),
('en', 'system_settings_card_desc',      'Org-wide defaults: language, timezone, time format, logo',  'system'),
('en', 'system_settings_title',          'System Settings',                                            'system'),
('en', 'system_settings_subtitle',       'These defaults apply to the whole system, including all companies.', 'system'),
('en', 'default_language_label',         'Default Language',                                           'system'),
('en', 'default_timezone_label',         'System Timezone',                                            'system'),
('en', 'default_time_format_label',      'Default Time Format',                                        'system'),
('en', 'time_format_12h',                '12-hour (AM/PM)',                                            'system'),
('en', 'time_format_24h',                '24-hour',                                                     'system'),
('en', 'system_logo_label',              'System Logo',                                                'system'),
('en', 'system_logo_hint',               'Shown on the login page and in the app header.',            'system'),
('en', 'upload_logo_btn',                'Upload Logo',                                                'system'),
('en', 'missing_translations_banner',    '{{count}} fields are missing a translation for this language.', 'system'),
('en', 'auto_translate_missing_btn',     'Auto-translate missing fields with AI',                      'system'),
('en', 'translating_missing_fields',     'Translating missing fields…',                                'system'),
('en', 'no_missing_translations',        'All fields are translated for this language.',              'system'),
('en', 'system_settings_saved',          'System settings saved.',                                     'system');
