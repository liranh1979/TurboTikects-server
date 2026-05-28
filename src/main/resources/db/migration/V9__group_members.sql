-- ── Group membership join table ─────────────────────────────────
CREATE TABLE group_members (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NOT NULL,
    user_id  INT NOT NULL,
    UNIQUE KEY uq_group_user (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES user_groups(ref_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)  REFERENCES users(red_id)       ON DELETE CASCADE
);

-- ── UI translation strings ───────────────────────────────────────
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'members_btn',               'Members',                       'system'),
('en', 'group_members_title',       'Group Members',                 'system'),
('en', 'current_members_section',   'Current Members',               'system'),
('en', 'add_members_section',       'Add Members',                   'system'),
('en', 'no_members_yet',            'No members yet',                'system'),
('en', 'no_users_to_add',           'All users are already members', 'system'),
('en', 'members_count',             '{{count}} member(s)',           'system'),
('en', 'add_selected_btn',          'Add Selected',                  'system'),
('en', 'member_search_placeholder', 'Search users...',               'system');
