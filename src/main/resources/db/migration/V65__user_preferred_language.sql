-- Personal Settings: per-user language preference, overrides the system_settings org-wide default.
-- NULL means "use the system default" — no special-casing needed elsewhere.
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(5) NULL;

-- Translation keys for the Personal Settings modal (English only, per project convention)
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'personal_settings_title',       'Personal Settings',                                  'system'),
('en', 'personal_settings_btn_tooltip', 'Personal Settings',                                  'system'),
('en', 'my_profile_label',              'My Profile',                                         'system'),
('en', 'change_password_label',         'Change Password',                                    'system'),
('en', 'current_password_label',        'Current Password',                                   'system'),
('en', 'new_password_label',            'New Password',                                       'system'),
('en', 'confirm_password_label',        'Confirm New Password',                               'system'),
('en', 'personal_language_label',       'My Language',                                        'system'),
('en', 'use_system_default_label',      'Use system default',                                 'system'),
('en', 'password_changed_success',      'Password changed successfully.',                     'system'),
('en', 'current_password_incorrect',    'Current password is incorrect.',                     'system'),
('en', 'synced_account_no_password',    'Your password is managed by your organization and cannot be changed here.', 'system'),
('en', 'passwords_do_not_match',        'New password and confirmation do not match.',        'system'),
('en', 'profile_saved_success',         'Profile saved.',                                      'system'),
('en', 'username_label',                'Username',                                            'system'),
('en', 'col_email',                     'Email',                                               'system');
