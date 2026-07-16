-- Simple action items in the library now get their own small set of fields (SimpleItemFieldsEditor
-- builds them, SimpleItemFieldsForm fills them in on a real ticket) — new UI strings for both.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'simple_item_fields_empty', 'No fields yet — add one to collect information when this item is worked.', 'system'),
    ('en', 'simple_item_field_label_placeholder', 'Field label, e.g. Laptop Model', 'system'),
    ('en', 'simple_item_field_type_text', 'text', 'system'),
    ('en', 'simple_item_field_type_number', 'number', 'system'),
    ('en', 'simple_item_field_type_date', 'date', 'system'),
    ('en', 'simple_item_field_type_checkbox', 'checkbox', 'system'),
    ('en', 'simple_item_add_field_btn', 'Add field', 'system'),
    ('en', 'simple_item_field_value_placeholder', 'Enter {{label}}', 'system'),
    ('en', 'simple_item_fields_label', 'FIELDS (optional)', 'system'),
    ('en', 'saved', 'Saved', 'system');
