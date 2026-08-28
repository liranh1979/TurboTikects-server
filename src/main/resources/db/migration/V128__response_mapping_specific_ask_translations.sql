-- "Map Response Fields" ("Auto-map from this response") could only be steered by the broad
-- "intent" set back at Step 1 (or nothing at all, from the Designer/Library, which never passes
-- one) — no way to say "actually, keep these five fields separate, don't combine them" without
-- recreating the whole action item. The backend already supported a per-run "specificAsk" field
-- (AiRefineResponseMappingRequestDto) but no frontend caller ever collected or sent it — added a
-- textarea in TestActionModal.tsx so the admin can refine the ask and re-run the mapping against
-- the SAME already-captured response (no live tool call needed again).
-- Also: the Workflow Designer's "Test this call now" button lived only up in the API
-- CALLS/MCP TOOL CALLS section header, nowhere near the RESPONSE DATA section below it — an admin
-- looking there for a way to test/map found nothing ("I don't see the step that testing the
-- mapping"). Added a second "Test & Map with AI" entry point directly in RESPONSE DATA's own
-- header (Designer only — the guided AI Workflow Builder wizard already has its own dedicated
-- Test step and doesn't need this).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'test_action_specific_ask_label',       'Refine the mapping (optional)',                                                    'system'),
('en', 'test_action_specific_ask_placeholder', 'e.g. keep each field separate — don''t combine name/city/country into one',       'system'),
('en', 'test_action_specific_ask_hint',        'Re-runs against the same captured response above — no need to call the tool again.', 'system'),
('en', 'response_mapping_test_map_btn',        'Test & Map with AI',                                                                'system');
