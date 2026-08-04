-- The response-capture editor (select a field, choose JSONPath or "AI: describe what to extract",
-- introduced in V110) previously lived only in Step 3's per-call editor — an admin landing on Step
-- 5 ("Response Mapping"), the step whose name matches what they're looking for, had no way to
-- define/see a capture there at all. Now rendered in both steps (shared state).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_define_captures_label', 'Captures for "{{name}}"', 'system');
