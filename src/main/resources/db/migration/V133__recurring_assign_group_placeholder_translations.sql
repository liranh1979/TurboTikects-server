-- Recurring Tickets: "Assign to Group" select gained a real placeholder option and an
-- empty-state hint, since the group list (GroupRepository.findAssignableGroups — only groups
-- holding TICKET_MANAGER) can legitimately be empty and previously rendered as a blank native
-- <select> with zero <option> elements.

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'recurring_form_assign_group_none',       '— Unassigned —', 'system'),
    ('en', 'recurring_form_assign_group_empty_hint', 'No groups have the Ticket Manager permission yet — grant it in Users & Groups to make them selectable here.', 'system');
