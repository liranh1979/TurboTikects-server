-- Pre-built "Approval Flow" workflow field for ticket templates.
-- Appears in Template Builder → Available Fields without any manual setup.
INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'approval_flow', 'workflow', 0, 1, 100, 0, NULL);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type)
VALUES
    ('en', 'approval_flow', 'Approval Flow', 'ticket_fields');
