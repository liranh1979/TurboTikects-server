-- Pre-built system fields for the Ticket entity.
-- is_system=1 marks them as platform fields (not deletable via UI).
-- field_options JSON stores the combobox values for the status field.

INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'title',                  'text',     1, 1, 1, 1, NULL),
    ('ticket', 'description',            'text',     0, 1, 2, 1, NULL),
    ('ticket', 'status',                 'combobox', 1, 1, 3, 1, '["new","open","completed","closed","rejected","pending","blocked"]'),
    ('ticket', 'request_user',           'text',     1, 1, 4, 1, NULL),
    ('ticket', 'responsible',            'text',     1, 1, 5, 1, NULL),
    ('ticket', 'expected_solution_time', 'date',     1, 1, 6, 1, NULL),
    ('ticket', 'activity',               'text',     0, 1, 7, 1, NULL),
    ('ticket', 'attachments',            'text',     0, 1, 8, 1, NULL);

-- English labels for the ticket fields (type = 'ticket_fields')
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'title',                  'Title',                   'ticket_fields'),
    ('en', 'description',            'Description',             'ticket_fields'),
    ('en', 'status',                 'Status',                  'ticket_fields'),
    ('en', 'request_user',           'Request User',            'ticket_fields'),
    ('en', 'responsible',            'Responsible Admin/Group', 'ticket_fields'),
    ('en', 'expected_solution_time', 'Expected Solution Time',  'ticket_fields'),
    ('en', 'activity',               'Activity',                'ticket_fields'),
    ('en', 'attachments',            'Attachments',             'ticket_fields');

-- Status combobox option translations (key pattern: {field_key}_opt_{value})
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'status_opt_new',       'New',       'ticket_fields'),
    ('en', 'status_opt_open',      'Open',      'ticket_fields'),
    ('en', 'status_opt_completed', 'Completed', 'ticket_fields'),
    ('en', 'status_opt_closed',    'Closed',    'ticket_fields'),
    ('en', 'status_opt_rejected',  'Rejected',  'ticket_fields'),
    ('en', 'status_opt_pending',   'Pending',   'ticket_fields'),
    ('en', 'status_opt_blocked',   'Blocked',   'ticket_fields');
