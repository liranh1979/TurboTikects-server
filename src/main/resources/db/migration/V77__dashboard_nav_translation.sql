-- "Dashboard" sidebar nav item label, consumed live via GET /api/v1/locales/{lang}.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'dashboard_nav_item', 'Dashboard', 'system');
