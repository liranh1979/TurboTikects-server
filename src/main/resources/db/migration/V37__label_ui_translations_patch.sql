-- Patch: label UI translation keys that were missing from V36
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'label_management',     'Label Management',                        'system'),
    ('en', 'create_label',         'New Label',                               'system'),
    ('en', 'label_key_label',      'Label Key',                               'system'),
    ('en', 'label_color_label',    'Color',                                   'system'),
    ('en', 'no_labels',            'No labels yet. Create your first label.', 'system'),
    ('en', 'delete_label',         'Delete Label',                            'system'),
    ('en', 'label_field_group',    'Labels',                                  'system'),
    ('en', 'label_defined_fields', 'Ticket label definitions',                'system');
