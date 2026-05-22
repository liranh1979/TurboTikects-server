-- Seed English label translations for the pre-seeded field_definitions rows.
-- translation_key must match field_definitions.field_key so the grid can look them up.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'role',          'Role',          'user_fields'),
    ('en', 'department',    'Department',    'user_fields'),
    ('en', 'notifications', 'Notifications', 'user_fields');