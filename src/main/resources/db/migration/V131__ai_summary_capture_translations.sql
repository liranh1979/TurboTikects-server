-- "AI Summary" response capture (mcp_tool's Map & Verify Output step): selecting a whole
-- array/object branch in the Visual JSON Explorer (the 🤖 button next to its bracket, distinct
-- from the plain leaf-click "exact value" mapping) asks the AI to turn it into a human-readable
-- HTML summary for a "rich-text" workflow field, evaluated live both in Verify Mapping and at real
-- ticket-execution time — see AiSettingsService.summarizeAsHtml / McpActionExecutor.
-- applyAiSummaryCapture. Also widened the Test / Map & Verify Output steps (were capped at the
-- wizard's default 720px, far too narrow for the JSON tree + wide tables) — see .awb-wrap--wide.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'awb_summarize_label',                 'AI Summary of',                                              'system'),
('en', 'awb_object_preview',                  'object',                                                     'system'),
('en', 'awb_summary_instruction_label',       'What should the summary focus on? (optional)',              'system'),
('en', 'awb_summary_instruction_placeholder', 'e.g. highlight price and duration first',                    'system'),
('en', 'awb_no_richtext_fields_hint',         'No rich-text workflow fields yet — type a name below to create one.', 'system'),
('en', 'awb_new_richtext_field_placeholder',  '…or type a new rich-text field name to create',              'system'),
('en', 'awb_new_field_placeholder',           '…or type a new field name to create',                        'system');
