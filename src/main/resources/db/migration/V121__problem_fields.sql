-- Problem Management: 4 new custom (non-system) ticket fields for the upcoming "Problem"
-- template. Unlike description/solution these are NOT universal system fields, so they're
-- not backed by real tickets columns — they live in the existing ticketData JSON bag, same as
-- every other template-specific field (e.g. timer fields). See
-- V2/Problem Management/01-data-model.html for the full design writeup.
--
-- Two new generic field_types are introduced here that the frontend's generic field renderer
-- needs to grow support for (not hardcoded to these field keys, reusable by any future template):
--   'richtext' — same rich-text-with-uploads editor the 'solution' system field already uses.
--   'boolean'  — a checkbox (today only used for the unrelated user-settings 'notifications'
--                field, which never reaches the ticket form renderer).
INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'root_cause',     'richtext', 0, 1, 100, 0, NULL),
    ('ticket', 'workaround',     'richtext', 0, 1, 101, 0, NULL),
    ('ticket', 'known_error',    'boolean',  1, 1, 102, 0, NULL),
    ('ticket', 'problem_status', 'combobox', 1, 1, 103, 0, '["open","under_investigation","known_error","resolved"]');

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'root_cause',     'Root Cause',     'ticket_fields'),
    ('en', 'workaround',     'Workaround',     'ticket_fields'),
    ('en', 'known_error',    'Known Error',    'ticket_fields'),
    ('en', 'problem_status', 'Problem Status', 'ticket_fields');

-- Problem_status combobox option translations (same key pattern as V30's status_opt_*)
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'problem_status_opt_open',                 'Open',                 'ticket_fields'),
    ('en', 'problem_status_opt_under_investigation',  'Under Investigation',  'ticket_fields'),
    ('en', 'problem_status_opt_known_error',           'Known Error',          'ticket_fields'),
    ('en', 'problem_status_opt_resolved',              'Resolved',             'ticket_fields');
