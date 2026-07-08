-- FEAT-01: Priority field for tickets (system field, dedicated column — same pattern as `status`).

ALTER TABLE tickets
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'medium' AFTER status;

INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'priority', 'combobox', 1, 1, 10, 1, '["critical","high","medium","low"]');

-- Field label + option labels — type='ticket_fields' (mirrors status/status_opt_* exactly;
-- feeds the Field Definitions admin editor + Template Builder preview, NOT the live t() bundle).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'priority',              'Priority', 'ticket_fields'),
    ('en', 'priority_opt_critical', 'Critical', 'ticket_fields'),
    ('en', 'priority_opt_high',     'High',     'ticket_fields'),
    ('en', 'priority_opt_medium',   'Medium',   'ticket_fields'),
    ('en', 'priority_opt_low',      'Low',      'ticket_fields');

-- Generic UI copy consumed live via GET /api/v1/locales/{lang} — type='system'.
-- (column header, filter label, and the 4 static SLA reference strings for the picker buttons —
-- these are pure display copy, NOT functional SLA tracking/enforcement; see future FEAT-02.)
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'ticket_priority_col',    'Priority',                    'system'),
    ('en', 'ticket_filter_priority', 'Priority',                    'system'),
    ('en', 'priority_sla_critical',  'Response: 15m • Resolve: 1h', 'system'),
    ('en', 'priority_sla_high',      'Response: 1h • Resolve: 4h',  'system'),
    ('en', 'priority_sla_medium',    'Response: 4h • Resolve: 8h',  'system'),
    ('en', 'priority_sla_low',       'Response: 8h • Resolve: 3d',  'system');
