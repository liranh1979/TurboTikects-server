-- Step 1 form section headers and computed Bind DN label
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'ldap_section_auth',         'Authentication',   'system'),
('en', 'ldap_section_search_base',  'Search Base',      'system'),
('en', 'ldap_bind_dn_computed',     'Computed Bind DN', 'system');
