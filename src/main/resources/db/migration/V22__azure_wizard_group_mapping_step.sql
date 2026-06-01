-- Split azure wizard Step 3 into User Mapping + Group Mapping
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'azure_step_user_mapping',  'User Mapping',  'system'),
('en', 'azure_step_group_mapping', 'Group Mapping', 'system');
