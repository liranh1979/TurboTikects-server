-- Missing UI strings from LDAP wizard steps 2-4 and the missing-fields detour flow

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES

-- Common button missing from V1-V4
('en', 'next_btn',                    'Next',                                                                               'system'),

-- Step 1
('en', 'ldap_bind_pw_placeholder',    '(leave blank to keep existing)',                                                     'system'),

-- Step 2 — sample tables
('en', 'ldap_attr_col',               'Attribute',                                                                          'system'),
('en', 'ldap_val_col',                'Value',                                                                              'system'),
('en', 'ldap_no_users_in_dn',         'No users found in this base DN.',                                                    'system'),
('en', 'ldap_no_groups_in_dn',        'No groups found in this base DN.',                                                   'system'),
('en', 'ldap_no_group_base_dn',       'No Group Base DN configured — group sync skipped.',                                  'system'),

-- Step 3 — mapping table
('en', 'ldap_sample_col',             'Sample',                                                                             'system'),
('en', 'ldap_section_users',          'Users',                                                                              'system'),
('en', 'ldap_section_groups',         'Groups',                                                                             'system'),
('en', 'ldap_no_mappings',            'No mappings suggested. Try re-running the AI mapping.',                              'system'),

-- Step 4 — confirm card
('en', 'ldap_confirm_conn_name',      'Connection Name',                                                                    'system'),
('en', 'ldap_confirm_host',           'Host',                                                                               'system'),
('en', 'ldap_confirm_user_mappings',  'User Mappings',                                                                      'system'),
('en', 'ldap_confirm_group_mappings', 'Group Mappings',                                                                     'system'),
('en', 'ldap_fields_mapped',          '{{count}} fields mapped',                                                            'system'),
('en', 'ldap_initial_sync_label',     'Run initial sync now?',                                                              'system'),

-- Missing-fields detour (FieldDefinitionsManager + FieldDefinitionsSidebar)
('en', 'ldap_detour_banner_title',    'Creating fields for LDAP Wizard',                                                    'system'),
('en', 'ldap_detour_banner_desc',     'Add the suggested fields below, then click "Return to LDAP Wizard" to re-run the mapping.', 'system'),
('en', 'ldap_return_to_wizard_btn',   '← Return to LDAP Wizard',                                                           'system'),
('en', 'ldap_suggested_by_section',   'Suggested by LDAP',                                                                  'system'),
('en', 'ldap_use_suggestion_btn',     '↓ Use This',                                                                        'system');
