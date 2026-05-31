-- Common action button labels missing from earlier migrations
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'edit_btn',   'Edit',   'system'),
('en', 'delete_btn', 'Delete', 'system');
