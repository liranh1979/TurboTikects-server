-- All user-facing UI strings that were hardcoded in components
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES

-- Loading states
('en', 'loading_custom_fields',       'Loading Custom Fields...',           'system'),
('en', 'loading_system_fields',       'Loading System Fields...',           'system'),
('en', 'loading_ai_manager',          'Loading AI Manager...',              'system'),

-- Save / dirty state feedback
('en', 'saved_successfully',          'Saved successfully',                 'system'),
('en', 'save_failed',                 'Save failed, please try again',      'system'),
('en', 'unsaved_changes',             'Unsaved changes',                    'system'),
('en', 'fields_count',                '{{count}} field(s)',                 'system'),
('en', 'fields_count_defined',        '{{count}} field(s) defined',         'system'),

-- Confirm dialogs
('en', 'confirm_discard_changes',     'You have unsaved changes. Switch language and discard them?', 'system'),
('en', 'confirm_bulk_translate',      'Auto-translate all fields to {{lang}}?',                     'system'),
('en', 'confirm_delete_language',     'Delete {{lang}}? This will permanently remove all its translations.', 'system'),

-- Sidebar labels
('en', 'languages_label',             'Languages',                          'system'),

-- Language management
('en', 'lang_code_placeholder',       'code (e.g. fr)',                     'system'),
('en', 'lang_name_placeholder',       'Name (e.g. French)',                 'system'),
('en', 'error_delete_language',       'Could not delete language',          'system'),

-- AI Manager
('en', 'error_activating_provider',   'Error activating provider',          'system'),
('en', 'error_deleting_provider',     'Error deleting provider',            'system'),
('en', 'error_adding_provider',       'Error adding AI provider',           'system'),
('en', 'ai_testing',                  'Testing...',                         'system'),
('en', 'ai_test_success',             'OK',                                 'system'),
('en', 'ai_test_error',               'Error',                              'system'),
('en', 'ai_test_btn',                 'Test',                               'system'),
('en', 'provider_placeholder',        'Provider (e.g. OpenAI)',             'system'),
('en', 'model_placeholder',           'Model (e.g. gpt-4o)',                'system'),
('en', 'api_key_placeholder',         'API Key',                            'system'),
('en', 'base_url_placeholder',        'Base URL (Optional)',                'system'),

-- User Form Drawer
('en', 'edit_user',                   'Edit User',                          'system'),
('en', 'new_user',                    'New User',                           'system'),
('en', 'basic_info',                  'Basic Info',                         'system'),
('en', 'custom_fields_section',       'Custom Fields',                      'system'),
('en', 'new_password',                'New Password',                       'system'),
('en', 'password_optional_note',      'leave blank to keep current',        'system'),
('en', 'username_placeholder',        'e.g. jdoe',                         'system'),
('en', 'display_name_placeholder',    'e.g. John Doe',                     'system'),
('en', 'password_placeholder',        'Enter password',                     'system'),
('en', 'yes',                         'Yes',                                'system'),
('en', 'no',                          'No',                                 'system'),
('en', 'create_user',                 'Create User',                        'system'),
('en', 'error_username_required',     'Username is required',               'system'),
('en', 'error_password_required',     'Password is required',               'system'),
('en', 'error_display_name_required', 'Display name is required',           'system'),
('en', 'error_occurred',              'An error occurred',                  'system');
