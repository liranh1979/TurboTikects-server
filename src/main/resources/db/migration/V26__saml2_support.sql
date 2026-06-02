-- Add SAML2 SP Entity ID override to azure_configs
ALTER TABLE azure_configs
    ADD COLUMN saml_sp_entity_id VARCHAR(255) NULL;

-- SAML2 wizard UI strings
INSERT INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'saml_setup_title',         'Azure AD SAML2 Setup',                                          'system'),
('en', 'saml_setup_desc',          'Register these values in your Azure AD Enterprise Application to enable SAML login:', 'system'),
('en', 'saml_sp_entity_id_label',  'SP Entity ID (Identifier)',                                     'system'),
('en', 'saml_acs_url_label',       'Reply URL (ACS URL)',                                            'system'),
('en', 'saml_login_url_label',     'Sign-On URL',                                                   'system'),
('en', 'saml_metadata_url_label',  'SP Metadata URL',                                               'system'),
('en', 'saml_copy_btn',            'Copy',                                                          'system'),
('en', 'saml_copied',              'Copied!',                                                       'system'),
('en', 'saml_sp_entity_id_hint',   'Leave blank to use auto-generated ID (urn:turbotikects:azure:{id})', 'system');
