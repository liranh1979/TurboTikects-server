-- "Clone Ticket" button on the ticket edit page.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type)
VALUES
    ('en', 'ticket_clone_btn', 'Clone Ticket', 'system'),
    ('en', 'ticket_clone_error', 'Couldn''t clone this ticket. Try again.', 'system'),
    ('en', 'ticket_clone_disabled_dirty', 'Save your changes first', 'system'),
    ('en', 'ticket_clone_copy_suffix', '(Copy)', 'system');
