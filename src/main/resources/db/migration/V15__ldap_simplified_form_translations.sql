-- Simplified LDAP Step 1 form: Domain + Username replace raw Bind DN
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'ldap_field_domain',        'Domain',                                  'system'),
('en', 'ldap_field_username',      'Username',                                'system'),
('en', 'ldap_field_domain_hint',   'e.g. company.com',                        'system'),
('en', 'ldap_field_username_hint', 'e.g. administrator',                      'system');
