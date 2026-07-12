-- Pre-built "Related Tickets" field for ticket templates (converts FEAT-04's
-- fixed page section into an admin-configurable field, same pattern as the
-- "Approval Flow" workflow field in V62).
-- Appears in Template Builder → Available Fields without any manual setup.
INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'ticket_relations', 'ticket_relations', 0, 1, 101, 0, NULL);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type)
VALUES
    ('en', 'ticket_relations', 'Related Tickets', 'ticket_fields');
