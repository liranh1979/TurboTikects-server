-- Allow the same translation_key to exist for different entity types
-- (e.g. both user_fields and group_fields can have a 'role' key)
ALTER TABLE dynamic_translations DROP INDEX lang_key;
ALTER TABLE dynamic_translations ADD UNIQUE KEY lang_key (lang_code, translation_key, type);

-- UI strings for Group Custom Fields
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'field_group_groups',         'Group Custom Fields',        'system'),
    ('en', 'group_defined_fields',       'Group-defined fields',       'system'),
    ('en', 'manage_group_custom_fields', 'Manage Group Custom Fields', 'system');
