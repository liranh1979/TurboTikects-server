-- AI Workflow Builder's Test step (TestActionModal.tsx): the CALL TRACE box only ever rendered
-- responsePreview, which the backend caps at 2000 chars mid-string with no regard for JSON
-- structure — a real API response bigger than that looked "broken" (a dangling key, no closing
-- braces) to the admin, even though the AI's own "Map Response Fields" step already grounds on the
-- much fuller rawResponse (up to 200,000 chars). Added a "Show full response"/"Show less" toggle
-- so the admin can see the same fuller text the AI actually uses.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'test_action_show_full_response_btn', 'Show full response', 'system'),
    ('en', 'test_action_show_less_btn',           'Show less',          'system');
