ALTER TABLE field_definitions
    ADD COLUMN is_admin_only TINYINT(1) NOT NULL DEFAULT 0;

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
  ('en', 'field_admin_only',     'Admin only',  'system'),
  ('en', 'field_visibility_all', 'All users',   'system');
