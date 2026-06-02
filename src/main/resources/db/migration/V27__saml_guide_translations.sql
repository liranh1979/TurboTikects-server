-- SSO wizard setup guide and metadata test UI strings
INSERT INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'saml_guide_step1_title',   'Step 1: Configure Azure AD',                                                                                          'system'),
('en', 'saml_guide_step1_desc',    'In Azure AD Portal → Enterprise Applications → [Your App] → Single sign-on → SAML, fill in Basic SAML Configuration:', 'system'),
('en', 'saml_guide_azure_identifier', 'Identifier (Entity ID)',                                                                                            'system'),
('en', 'saml_guide_azure_reply_url',  'Reply URL (ACS URL)',                                                                                               'system'),
('en', 'saml_guide_azure_signon_url', 'Sign on URL (optional)',                                                                                            'system'),
('en', 'saml_guide_step2_title',   'Step 2: No Certificate Setup Needed',                                                                                  'system'),
('en', 'saml_guide_step2_desc',    'Azure''s signing certificate and SSO endpoint are automatically fetched using your Tenant ID. The Federation Metadata Document does not need to be pasted anywhere in this application.', 'system'),
('en', 'saml_test_metadata_btn',   'Test SAML Connection',                                                                                                 'system'),
('en', 'saml_test_metadata_ok',    'SAML metadata fetched successfully',                                                                                   'system'),
('en', 'saml_test_metadata_fail',  'Failed to fetch SAML metadata',                                                                                        'system'),
('en', 'saml_test_metadata_testing', 'Testing…',                                                                                                           'system');
