-- AI Workflow Builder, Response Mapping step (external_api): "Run a real test" (a second live
-- call to the real API, redundant with the Test step and risky for non-idempotent APIs) is
-- replaced with "Verify Captures" — re-evaluates the applied JsonPaths against the response
-- already fetched in the Test step, no new live call.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_verify_captures_label',   'Verify Captures',                                                                      'system'),
    ('en', 'awb_verify_captures_hint',    'Re-checks your JSONPaths against the response already captured in the Test step — no new live call is made.', 'system'),
    ('en', 'awb_verify_captures_btn',     'Verify Captures',                                                                      'system'),
    ('en', 'awb_verifying_captures_ellipsis', 'Verifying…',                                                                        'system'),
    ('en', 'awb_verify_captures_failed',  'Could not verify captures.',                                                           'system'),
    ('en', 'awb_verify_captures_empty',   'Nothing captured — check the JSONPaths above against the response.',                   'system');
