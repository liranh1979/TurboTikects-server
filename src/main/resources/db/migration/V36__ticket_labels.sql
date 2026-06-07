CREATE TABLE IF NOT EXISTS ticket_labels (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    label_key  VARCHAR(64) NOT NULL,
    color      VARCHAR(16) NOT NULL DEFAULT '#3b82f6',
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_label_key (label_key)
);

-- Labels as a mandatory ticket system field
INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'labels', 'labels', 1, 1, 9, 1, NULL);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'labels', 'Labels', 'ticket_fields');

-- UI strings
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'labels_settings',          'Labels',                                 'system'),
    ('en', 'label_management',         'Label Management',                       'system'),
    ('en', 'create_label',             'New Label',                              'system'),
    ('en', 'label_key_label',          'Label Key',                              'system'),
    ('en', 'label_color_label',        'Color',                                  'system'),
    ('en', 'no_labels',                'No labels yet. Create your first label.','system'),
    ('en', 'delete_label',             'Delete Label',                           'system'),
    ('en', 'label_field_group',        'Labels',                                 'system'),
    ('en', 'label_defined_fields',     'Ticket label definitions',               'system');

-- Seed starter labels with type="label" (the translatable display name)
INSERT IGNORE INTO ticket_labels (label_key, color) VALUES
    ('bug',      '#ef4444'),
    ('feature',  '#3b82f6'),
    ('urgent',   '#f97316'),
    ('security', '#8b5cf6');

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'bug',      'Bug',      'label'),
    ('en', 'feature',  'Feature',  'label'),
    ('en', 'urgent',   'Urgent',   'label'),
    ('en', 'security', 'Security', 'label');
