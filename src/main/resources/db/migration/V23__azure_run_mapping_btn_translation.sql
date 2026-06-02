-- Manual trigger button for group AI mapping step
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'azure_run_mapping_btn', 'Run AI Mapping', 'system');
