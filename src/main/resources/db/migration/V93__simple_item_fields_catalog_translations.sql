-- SimpleItemFieldsEditor now picks fields from the field_definitions catalog (entity_type='workflow',
-- managed via Workflow Fields Manager) instead of letting an admin free-type a label/key.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'simple_item_fields_no_catalog', 'No workflow fields defined yet — add some in Settings → Fields → Workflow Fields first.', 'system'),
    ('en', 'simple_item_field_picker_placeholder', '— Select a workflow field —', 'system');
