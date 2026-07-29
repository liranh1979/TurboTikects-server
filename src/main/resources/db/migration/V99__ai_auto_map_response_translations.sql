-- "Auto-map from this response" — after a real "Test this call now" run, the AI re-derives
-- responseCaptures/resultPath + fieldMappings.response grounded in the REAL captured response
-- instead of a guess from prose docs. Added to TestActionModal.tsx (shared by the Designer and,
-- newly, the AI Workflow Builder wizard's own Step 2 "Test this call now" — reusing the existing
-- 'test_action_btn' key already seeded for the Designer).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'test_action_auto_map_btn', 'Auto-map from this response', 'system'),
    ('en', 'test_action_auto_map_running', 'Analyzing response…', 'system'),
    ('en', 'test_action_auto_map_failed', 'Could not generate a mapping suggestion.', 'system'),
    ('en', 'test_action_proposed_changes_heading', 'PROPOSED MAPPING CHANGES', 'system'),
    ('en', 'test_action_apply_mapping_btn', 'Apply to draft', 'system');
