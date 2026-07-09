-- Heading/subheading copy for the redesigned split-screen login form, consumed
-- live via GET /api/v1/locales/{lang}.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'login_heading', 'Welcome back', 'system'),
    ('en', 'login_subheading', 'Sign in to your account to continue', 'system');
