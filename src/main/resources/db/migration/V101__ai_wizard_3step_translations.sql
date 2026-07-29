-- AI Action Item Wizard rebuild: explicit 3-step flow (Describe & Paste → Test & Map → Review &
-- Save) with a "Match Fields with AI" step and draft-save support. New UI strings for all of it.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_step_test_map', 'Test & Map', 'system'),
    ('en', 'awb_step_review_save', 'Review & Save', 'system'),
    ('en', 'awb_matched_fields_label', 'Input fields for this call', 'system'),
    ('en', 'awb_matched_fields_hint', 'Matched from your documentation — add or remove as needed.', 'system'),
    ('en', 'awb_match_fields_btn', 'Match Fields with AI', 'system'),
    ('en', 'awb_matching_ellipsis', 'Matching…', 'system'),
    ('en', 'awb_match_fields_failed', 'Could not match fields.', 'system'),
    ('en', 'awb_no_matched_fields', 'No input fields matched yet.', 'system'),
    ('en', 'awb_add_field_placeholder', 'Search fields to add…', 'system'),
    ('en', 'awb_matched_field_usedby', 'matched input field', 'system'),
    ('en', 'awb_run_test_label', 'Run a real test', 'system'),
    ('en', 'awb_run_test_hint', 'A real response is required before AI can map response fields — nothing is guessed here.', 'system'),
    ('en', 'awb_no_calls_yet', 'Add at least one call above before testing.', 'system'),
    ('en', 'awb_save_draft_btn', 'Save as Draft', 'system'),
    ('en', 'awb_continue_btn', 'Continue', 'system'),
    ('en', 'awb_draft_name_required', 'Enter a name before saving as a draft.', 'system'),
    ('en', 'awb_save_complete_btn', 'Save & Complete', 'system'),
    ('en', 'awb_status_draft_badge', 'Draft', 'system'),
    ('en', 'awb_status_complete_badge', 'Complete', 'system'),
    ('en', 'remove', 'Remove', 'system');

-- Existing key, text updated: this button now fires from Step 2's "Test & Map" (inline, always
-- part of the page) rather than a Designer-only modal add-on — "Map Response Fields" describes
-- its role in the new flow more clearly than the original "Auto-map from this response" copy.
UPDATE dynamic_translations
SET translated_text = 'Map Response Fields'
WHERE lang_code = 'en' AND translation_key = 'test_action_auto_map_btn' AND type = 'system';
