-- AI Workflow Builder: "Match Fields with AI" is promoted from a panel inside Step 1 into its own
-- Step 2 "Field Mapping" (wizard becomes 4 steps: Describe & Paste -> Field Mapping -> Test & Map ->
-- Review & Save). Admin instructions are now split into two parts (what the call should do vs. what
-- fields it needs), and the AI mapping call keeps a persisted conversation across "redefine
-- instructions" re-runs, alongside a new explicit manual-selection mode.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_step_field_mapping', 'Field Mapping', 'system'),
    ('en', 'awb_fields_instructions_label', 'What input fields does it need?', 'system'),
    ('en', 'awb_fields_instructions_placeholder', 'e.g. Needs the customer''s email address and the order ID', 'system'),
    ('en', 'awb_redefine_instructions_hint', 'Editing the instructions above and suggesting again continues the same AI conversation — it remembers what it already proposed.', 'system'),
    ('en', 'awb_mapping_mode_label', 'Field mapping', 'system'),
    ('en', 'awb_mapping_mode_ai_option', 'Suggest with AI', 'system'),
    ('en', 'awb_mapping_mode_manual_option', 'Select Manually', 'system'),
    ('en', 'awb_manual_fields_hint', 'Pick the fields this call needs as input.', 'system'),
    ('en', 'awb_manual_fields_empty', 'No fields selected yet.', 'system');
