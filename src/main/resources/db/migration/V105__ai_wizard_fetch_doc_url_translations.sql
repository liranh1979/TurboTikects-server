-- AI Workflow Builder, Documentation step (external_api): admin can now paste a documentation URL
-- and have the backend fetch its raw content server-side (avoiding a browser CORS block) straight
-- into the same Documentation textarea, instead of only ever pasting text by hand.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_documentation_url_label',       'Or fetch from a URL',                        'system'),
    ('en', 'awb_documentation_url_placeholder', 'https://example.com/api-docs',               'system'),
    ('en', 'awb_fetch_doc_btn',                 'Fetch',                                       'system'),
    ('en', 'awb_fetching_doc_ellipsis',         'Fetching…',                                   'system'),
    ('en', 'awb_fetch_doc_failed',              'Could not fetch this URL.',                  'system');
