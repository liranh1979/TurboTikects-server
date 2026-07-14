-- FEAT-02 Phase 1: register SLA as a locked (is_system=1) templatable field, mirroring the
-- Priority/Attachments pattern (V74/V30) rather than the removable Related Tickets/Workflow
-- pattern (V80/V62) — an admin accidentally hiding SLA visibility from a template is an
-- operational risk, not just a UX gap, so it can be repositioned but never removed.
INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'sla', 'sla', 0, 1, 102, 1, NULL);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type)
VALUES
    ('en', 'sla', 'SLA', 'ticket_fields');

-- Deliberate exception to this app's usual "seed the definition, let an admin add it manually per
-- template" precedent (every prior field — Priority, Workflow, Related Tickets — was seeded but
-- never backfilled): SLA visibility silently missing from a template is a real operational risk,
-- so this migration appends it to every existing template's current version. Idempotent via the
-- JSON_SEARCH guard (harmless to re-run, though Flyway won't re-run an already-applied migration
-- anyway).
UPDATE ticket_template_versions
SET layout = JSON_ARRAY_APPEND(layout, '$.tabs[0].fields',
    JSON_OBJECT(
        'fieldKey', 'sla',
        'fieldType', 'sla',
        'isSystem', TRUE,
        'displayOrder', 102,
        'defaultValue', '',
        'width', 'full',
        'fieldVisibility', 'all'
    )
)
WHERE is_current = 1
  AND JSON_SEARCH(layout, 'one', 'sla', NULL, '$.tabs[*].fields[*].fieldKey') IS NULL;

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'sla_no_policy_note', 'No SLA policy configured for this priority.', 'system');
