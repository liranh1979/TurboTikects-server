-- Hint explaining LDAP domain is not the server hostname
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'ldap_field_domain_desc', 'LDAP organizational domain — not the server hostname (e.g. example.com → dc=example,dc=com)', 'system');
