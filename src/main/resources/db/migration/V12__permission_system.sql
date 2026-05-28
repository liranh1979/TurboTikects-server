CREATE TABLE permissions (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    permission_key VARCHAR(64) NOT NULL UNIQUE,
    display_order  INT DEFAULT 0
);

INSERT INTO permissions (permission_key, display_order) VALUES
('MANAGE_USERS',     1),
('MANAGE_GROUPS',    2),
('MANAGE_FIELDS',    3),
('MANAGE_LANGUAGES', 4),
('MANAGE_AI',        5);

CREATE TABLE user_permissions (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT NOT NULL,
    permission_id INT NOT NULL,
    UNIQUE KEY uq_user_permission (user_id, permission_id),
    FOREIGN KEY (user_id)       REFERENCES users(red_id)       ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id)     ON DELETE CASCADE
);

CREATE TABLE group_permissions (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    group_id      INT NOT NULL,
    permission_id INT NOT NULL,
    UNIQUE KEY uq_group_permission (group_id, permission_id),
    FOREIGN KEY (group_id)      REFERENCES user_groups(ref_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id)     ON DELETE CASCADE
);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'permission_manage_users_name',      'Manage Users',                                           'system'),
('en', 'permission_manage_users_desc',      'Create, edit, and delete users; manage group memberships', 'system'),
('en', 'permission_manage_groups_name',     'Manage Groups',                                          'system'),
('en', 'permission_manage_groups_desc',     'Create, edit, and delete groups; manage group members',  'system'),
('en', 'permission_manage_fields_name',     'Manage Custom Fields',                                   'system'),
('en', 'permission_manage_fields_desc',     'Define and configure custom fields for users and groups','system'),
('en', 'permission_manage_languages_name',  'Manage Languages',                                       'system'),
('en', 'permission_manage_languages_desc',  'Add languages and manage UI translations',               'system'),
('en', 'permission_manage_ai_name',         'Manage AI Settings',                                     'system'),
('en', 'permission_manage_ai_desc',         'Configure AI providers and API keys',                    'system'),
('en', 'permissions_section',              'Permissions',                                             'system'),
('en', 'personal_permissions_label',       'Personal Permissions',                                    'system'),
('en', 'group_permissions_label',          'Via Group Membership',                                    'system'),
('en', 'no_permissions_assigned',          'No permissions assigned',                                 'system'),
('en', 'no_settings_access',              'You do not have access to any settings',                   'system'),
('en', 'permissions_note',                'Changes take effect on next login',                        'system');
