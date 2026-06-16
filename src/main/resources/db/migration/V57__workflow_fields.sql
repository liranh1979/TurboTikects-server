INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('workflow', 'title',           'text',     1, 1, 1, 1, NULL),
    ('workflow', 'workflow_status', 'combobox', 1, 1, 2, 1,
     '["pending","in_progress","blocked","done","canceled"]'),
    ('workflow', 'assigned',        'assignee', 1, 1, 3, 1, NULL);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'title',           'Title',       'workflow_fields'),
    ('en', 'workflow_status', 'Status',      'workflow_fields'),
    ('en', 'assigned',        'Assigned To', 'workflow_fields'),
    ('en', 'workflow_status_opt_pending',     'Pending',     'workflow_fields'),
    ('en', 'workflow_status_opt_in_progress', 'In Progress', 'workflow_fields'),
    ('en', 'workflow_status_opt_blocked',     'Blocked',     'workflow_fields'),
    ('en', 'workflow_status_opt_done',        'Done',        'workflow_fields'),
    ('en', 'workflow_status_opt_canceled',    'Canceled',    'workflow_fields');
