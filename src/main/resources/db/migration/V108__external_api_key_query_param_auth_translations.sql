-- External API call editor (Designer + AI Workflow Builder): auth.type="api_key" can now be sent
-- as a query parameter, not just a header — some real APIs (e.g. SerpAPI) require it that way, and
-- there was previously no admin-facing way to authenticate one of those short of a this.<key>
-- request-mapping placeholder with no UI to ever set its value, or hardcoding the real secret in
-- plaintext directly in the URL.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'auth_api_key_location_header_option', 'Send as header',                       'system'),
    ('en', 'auth_api_key_location_query_option',  'Send as query parameter',               'system'),
    ('en', 'eae_query_param_name_placeholder',    'Parameter name (default api_key)',      'system');

-- Existing key, text updated: "API key header" is no longer accurate now that this auth type can
-- also be sent as a query parameter.
UPDATE dynamic_translations
SET translated_text = 'API key'
WHERE lang_code = 'en' AND translation_key = 'auth_api_key_header_option' AND type = 'system';
