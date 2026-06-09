-- base_url column left in DB (nullable, so new inserts work fine); URL is now hardcoded per provider in the backend

-- UI translations for the provider selector
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'select_provider_placeholder', 'Select a provider', 'system'),
('en', 'provider_openai_label',       'OpenAI',            'system'),
('en', 'provider_anthropic_label',    'Anthropic (Claude)','system'),
('en', 'provider_gemini_label',       'Google Gemini',     'system'),
('en', 'model_hint_label',            'e.g. gpt-4o',       'system');
