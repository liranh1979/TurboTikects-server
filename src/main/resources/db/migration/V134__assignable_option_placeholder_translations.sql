-- Acceleration Rules and Reports: the "assign to user/group" and "recipient" selects had
-- hardcoded English placeholder/empty-state strings instead of routing through
-- dynamic_translations, and the Reports admin-user recipient multiselect had no empty-state
-- guard at all (same underlying pattern as the Recurring Tickets combobox fixed in V133,
-- though far lower risk here since its backing query is unfiltered by permission).

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'acceleration_no_users_option',          '— no users —', 'system'),
    ('en', 'acceleration_no_groups_option',         '— no groups —', 'system'),
    ('en', 'reports_recipient_group_placeholder',   '—', 'system'),
    ('en', 'reports_recipients_users_empty_hint',   'No admin users available.', 'system');
