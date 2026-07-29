-- AI Workflow Builder, Response Mapping step (external_api): the step now shows only the
-- response-side mapping table (the request table, already finished in Field Mapping, no longer
-- reappears here) and displays the actual captured JSON response from the Test step, so the
-- admin can see exactly what data the AI (and any manual edits) are working from.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_captured_response_label', 'Captured response (from the Test step)', 'system');
