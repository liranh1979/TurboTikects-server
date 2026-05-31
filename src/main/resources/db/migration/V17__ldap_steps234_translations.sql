-- UI strings for redesigned LDAP wizard Steps 2, 3 and 4
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'ldap_step2_intro',       'Load a sample record from each base DN to verify your connection paths.',          'system'),
('en', 'ldap_step3_intro',       'AI is analyzing your LDAP sample data to suggest field mappings.',                'system'),
('en', 'ldap_step4_ready',       'Ready to Activate',                                                               'system'),
('en', 'ldap_step4_intro',       'Review your configuration, then choose how to save.',                             'system'),
('en', 'ldap_sync_option_title', 'Run Initial Sync',                                                                'system'),
('en', 'ldap_sync_option_desc',  'Import users and groups from this LDAP connection immediately after saving.',      'system');
