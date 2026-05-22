-- UI translation keys for User Management and Background Tasks panels
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
-- Settings card
('en', 'settings_users_manager',      'User Management',                                         'system'),
-- Users page header
('en', 'user_management_title',        'User Management',                                         'system'),
('en', 'configure_columns',            'Configure Columns',                                       'system'),
('en', 'sync_fields_to_users',         'Sync Fields to All Users',                               'system'),
('en', 'sync_starting',                'Starting…',                                               'system'),
('en', 'loading_users',                'Loading Users…',                                          'system'),
-- Users table
('en', 'col_number',                   '#',                                                       'system'),
('en', 'col_username',                 'Username',                                                'system'),
('en', 'col_display_name',             'Display Name',                                            'system'),
('en', 'col_role',                     'Role',                                                    'system'),
('en', 'badge_super_admin',            'Super Admin',                                             'system'),
('en', 'badge_user',                   'User',                                                    'system'),
('en', 'no_users_found',               'No users found.',                                         'system'),
('en', 'users_visible_columns',        'column(s) visible',                                       'system'),
-- Configure columns modal
('en', 'configure_columns_desc',       'Choose which fields appear as columns in the users table.','system'),
('en', 'cancel_btn',                   'Cancel',                                                  'system'),
('en', 'save_changes_btn',             'Save',                                                    'system'),
-- Background tasks panel
('en', 'bg_tasks_title',               'Background Tasks',                                        'system'),
('en', 'tasks_running_label',          'running',                                                  'system'),
('en', 'task_status_running',          'running',                                                  'system'),
('en', 'task_status_completed',        'completed',                                               'system'),
('en', 'task_status_failed',           'failed',                                                  'system'),
('en', 'task_starting',                'Starting…',                                               'system');