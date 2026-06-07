ALTER TABLE ticket_templates
    ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0;

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
  ('en', 'set_as_default',      'Set as Default',   'system'),
  ('en', 'default_template',    'Default',           'system'),
  ('en', 'unset_default',       'Remove Default',    'system');
