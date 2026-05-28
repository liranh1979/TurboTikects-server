-- ── Add field_options JSON column to field_definitions ──────────
ALTER TABLE field_definitions ADD COLUMN field_options JSON NULL;

-- ── UI translation strings for combobox options management ──────
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'combobox_options_label',      'Options',        'system'),
('en', 'add_option_placeholder',      'Add option...',  'system'),
('en', 'combobox_select_placeholder', 'Select...',      'system'),
('en', 'edit_options_btn',            'Edit Options',   'system'),
('en', 'no_options_yet',              'No options yet', 'system');
