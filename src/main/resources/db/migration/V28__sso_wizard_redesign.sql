-- New translation keys for the 3-step SSO wizard layout
INSERT INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'saml_wizard_intro',
  'To enable SSO login for this Azure AD connection, complete the 3 steps below. Make sure you have an Enterprise Application in Azure AD with SAML single sign-on configured.',
  'system'),
('en', 'saml_guide_step2_configure_title', 'Step 2: Configure in TurboTikects', 'system'),
('en', 'sso_display_name_desc',
  'This text appears on the SSO button on the login screen (e.g. Sign in with Contoso Azure AD).',
  'system'),
('en', 'saml_guide_step3_title', 'Step 3: Verify the Connection', 'system'),
('en', 'saml_guide_step3_desc',
  'Azure''s signing certificate and SSO endpoint are automatically fetched using your Tenant ID. No certificate upload or Federation Metadata URL is needed.',
  'system');

-- Improve existing step 1 labels to include the step number and clearer path
UPDATE dynamic_translations
  SET translated_text = 'Step 1: Register in Azure AD'
  WHERE lang_code = 'en' AND translation_key = 'saml_guide_step1_title';

UPDATE dynamic_translations
  SET translated_text = 'Azure AD Portal → Enterprise Applications → [Your App] → Single sign-on → SAML → Basic SAML Configuration, enter these values:'
  WHERE lang_code = 'en' AND translation_key = 'saml_guide_step1_desc';
