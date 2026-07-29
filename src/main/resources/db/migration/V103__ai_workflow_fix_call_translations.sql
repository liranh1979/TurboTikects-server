-- AI Workflow Builder, Test & Map step: "Fix with AI" — when a real test fails, the admin can ask
-- the AI to re-fix the one call, continuing the Field Mapping step's AiChatSession instead of
-- starting from a blank prompt. New UI strings for that panel.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'test_action_fix_call_label', 'ASK AI TO FIX THIS CALL', 'system'),
    ('en', 'test_action_fix_call_placeholder', 'e.g. Use the real SerpAPI endpoint for Google Flights, not this one', 'system'),
    ('en', 'test_action_fix_call_btn', 'Fix with AI', 'system'),
    ('en', 'test_action_fixing_ellipsis', 'Fixing…', 'system'),
    ('en', 'test_action_fix_call_failed', 'Could not fix this call.', 'system'),
    ('en', 'test_action_fix_proposed_heading', 'PROPOSED FIX', 'system'),
    ('en', 'test_action_apply_fix_btn', 'Apply Fix', 'system');
