-- Translation keys for the AI Settings "fetch models from provider" picker (English only, per project convention)
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'fetch_models_btn',            'Fetch Models',                                    'system'),
('en', 'fetching_models',             'Fetching…',                                       'system'),
('en', 'fetch_models_error',          'Could not fetch models. Check the API key and try again.', 'system'),
('en', 'no_models_found_for_provider','No models found for this provider.',              'system');
