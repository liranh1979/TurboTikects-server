-- New reusable Action Item Library (see V90__action_item_library.sql): a new "Action Items" tab
-- in Template Management, an "Add from Library" picker in the Workflow Designer, and the AI
-- Workflow Builder now saving into this library instead of one specific template.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'workflow_add_from_library_btn', 'Add from Library', 'system'),
    ('en', 'workflow_library_empty', 'No action items in the library yet — build one in the Action Items tab.', 'system'),
    ('en', 'action_items_tab', 'Action Items', 'system'),
    ('en', 'action_item_library_delete_confirm', 'Delete this action item from the library?', 'system'),
    ('en', 'action_item_library_hint', 'Reusable action items — add one to any template from the Workflow Designer. Adding a copy here never changes other templates already using it.', 'system'),
    ('en', 'action_item_library_new_simple_btn', 'New Simple Action Item', 'system'),
    ('en', 'action_item_library_name_placeholder', 'e.g. Provision Access', 'system'),
    ('en', 'action_item_library_empty', 'No action items yet — build one here, or with the AI Workflow Builder tab.', 'system'),
    ('en', 'action_item_library_ai_badge', 'AI', 'system'),
    ('en', 'action_item_library_simple_badge', 'Simple', 'system'),
    ('en', 'awb_new_library_entry_option', 'New library entry', 'system'),
    ('en', 'awb_overwrite_existing_entry_none_option', 'Overwrite existing {{type}} entry (none yet)', 'system'),
    ('en', 'awb_overwrite_existing_entry_option', 'Overwrite existing {{type}} entry', 'system'),
    ('en', 'awb_save_to_library_btn', 'Save to Library', 'system'),
    ('en', 'awb_saved_body_library', '"{{title}}" was saved to the Action Item Library.', 'system'),
    ('en', 'awb_saved_body_library_suffix', 'Open the Action Items tab to manage it, or add it to any template from that template''s Workflow Designer.', 'system');

-- This key's meaning changed (used to say "...into the template.") now that the AI Builder saves
-- into the library instead of one specific template — update in place rather than leave stale.
UPDATE dynamic_translations
SET translated_text = 'Failed to save into the library.'
WHERE lang_code = 'en' AND type = 'system' AND translation_key = 'awb_save_failed';
