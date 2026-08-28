-- New AI Workflow Builder wizard step for mcp_tool: "Map & Verify Output" (splits the old combined
-- "Test & Map" step into its own Test step plus this one), per V2/mcp-server-management/
-- 07-mockup-response-mapping.html. Two ways to build the response mapping — click a value in the
-- Visual JSON Explorer, or tell the AI in plain text — both feeding the same captures/mappings
-- list, plus "Verify Mapping" to re-check every resultPath against the already-captured response
-- (via /templates/evaluate-response-captures, now dispatching to McpActionExecutor.
-- evaluateResponseCaptures for mcp_tool the same way it already did for external_api).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'awb_step_map_verify',      'Map & Verify Output',                                                                    'system'),
('en', 'awb_run_test_mcp_hint',    'Response mapping happens next, in Map & Verify Output — this step just needs one real response to map against.', 'system'),
('en', 'awb_map_verify_hint',      'Click a value in the real response to map it, or tell the AI what you need — both write into the same list below. Nothing here re-runs the tool.', 'system'),
('en', 'awb_json_explorer_label',  'Visual JSON Explorer',                                                                   'system'),
('en', 'awb_no_response_for_tree', 'No captured response for this call yet — go back to Test.',                             'system'),
('en', 'awb_clicked_label',        'Clicked',                                                                                'system'),
('en', 'awb_map_to_field_label',   'Map to field',                                                                           'system'),
('en', 'awb_add_mapping_btn',      'Add Mapping',                                                                            'system'),
('en', 'awb_ai_instructions_label','AI Instructions',                                                                        'system'),
('en', 'awb_verify_mapping_label', 'Verify Mapping',                                                                         'system'),
('en', 'awb_verify_mapping_hint',  'Re-checks every resultPath against the response already captured in Test — no new tool call is made.', 'system'),
('en', 'awb_verify_mapping_btn',   'Verify Mapping',                                                                         'system');
