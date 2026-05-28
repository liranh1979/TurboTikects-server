-- ── Groups table ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS groups (
    ref_id       INT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(64)  NOT NULL,
    group_metadata JSON NULL
);

-- ── UI translation strings for Users & Groups management ────────
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES

-- Settings card
('en', 'settings_users_groups_manager',  'Users & Groups',                                           'system'),

-- Hub page
('en', 'users_groups_management_title',  'Users & Groups Management',                                 'system'),
('en', 'users_entity',                   'Users',                                                     'system'),
('en', 'users_entity_desc',              'Manage users and custom user fields',                       'system'),
('en', 'groups_entity',                  'Groups',                                                    'system'),
('en', 'groups_entity_desc',             'Manage groups and custom group fields',                     'system'),

-- Groups list page
('en', 'group_management_title',         'Group Management',                                          'system'),
('en', 'loading_groups',                 'Loading Groups...',                                         'system'),
('en', 'sync_fields_to_groups',          'Sync Fields to All Groups',                                 'system'),
('en', 'no_groups_found',                'No groups found.',                                          'system'),
('en', 'new_group',                      'New Group',                                                 'system'),
('en', 'edit_group',                     'Edit Group',                                                'system'),
('en', 'col_group_name',                 'Group Name',                                                'system'),
('en', 'groups_count',                   '{{count}} group(s)',                                        'system'),
('en', 'groups_visible_columns',         'column(s) visible',                                         'system'),
('en', 'configure_columns_group_desc',   'Choose which fields appear as columns in the groups table.','system'),

-- Group form drawer
('en', 'group_name_label',               'Group Name',                                                'system'),
('en', 'group_name_placeholder',         'e.g. Support Team',                                        'system'),
('en', 'error_group_name_required',      'Group name is required',                                    'system'),

-- Delete confirm dialogs
('en', 'confirm_delete_user',            'Delete user "{{name}}" (@{{username}})? This cannot be undone.', 'system'),
('en', 'confirm_delete_group',           'Delete group "{{name}}"? This cannot be undone.',           'system'),
('en', 'super_admin_no_delete',          'Super admins cannot be deleted',                            'system'),

-- Users page count fix
('en', 'users_count',                    '{{count}} user(s)',                                         'system');
