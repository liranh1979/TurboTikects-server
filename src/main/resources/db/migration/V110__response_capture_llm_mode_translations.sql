-- New per-capture "AI: describe what to extract" mode (ExternalApiCallsEditor.tsx's CallRow
-- response-captures row) — an admin can now write a plain-language instruction instead of a
-- JSONPath, resolved live by the currently-active AI provider both in the wizard and at real
-- ticket-execution time.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'capture_mode_jsonpath',              'JSONPath',                                  'system'),
    ('en', 'capture_mode_llm',                   'AI: describe what to extract',              'system'),
    ('en', 'capture_llm_instruction_placeholder', 'e.g. the cheapest flight''s total price',   'system');

-- Existing key, text updated: captures can now also be AI-extracted, not only JSONPath — and
-- clarify that an AI-mode capture DOES make a live call (unlike the rest of "Verify Captures",
-- which is otherwise a no-new-live-call re-check).
UPDATE dynamic_translations
SET translated_text = 'Re-checks your captures against the response already captured in the Test step — no new live API call is made, but an "AI: describe what to extract" capture does call your configured AI provider.'
WHERE lang_code = 'en' AND translation_key = 'awb_verify_captures_hint' AND type = 'system';
