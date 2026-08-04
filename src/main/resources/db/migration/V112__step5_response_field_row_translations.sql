-- Step 5 "Response Mapping" redesign: replaces the old separate "define a capture" + "map capture
-- to field" two-list UI (V110/V111) with one row per field: pick the target field, describe what
-- the AI should pull for it, test that one row immediately. V111's "awb_define_captures_label" key
-- is now unused (superseded by "awb_response_fields_label" below) — left in place, harmless, same
-- as this project's established pattern for superseded translation rows elsewhere.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_response_fields_label', 'Response fields for "{{name}}"',                                          'system'),
    ('en', 'awb_response_fields_empty', 'No fields defined yet — add one to pull a value from this response.',     'system'),
    ('en', 'awb_field_test_empty',      'No value found.',                                                        'system'),
    ('en', 'awb_field_test_failed',     'Test failed.',                                                           'system'),
    ('en', 'test_btn',                  'Test',                                                                   'system');
