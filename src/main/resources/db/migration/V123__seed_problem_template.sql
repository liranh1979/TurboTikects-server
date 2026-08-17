-- Problem Management: seed the "Problem" ticket template itself. Template creation normally
-- happens through the admin Template Editor UI (no migration needed for that in general), but
-- this one is seeded here so the feature is usable/testable without a manual click-through
-- step, and so `is_internal` is guaranteed set correctly from the start. is_default stays 0 —
-- this must never be the template an end user lands on by default.
--
-- Field order mirrors V2/Problem Management/01-data-model.html's "Reused system field" table:
-- title, description, the 4 new custom fields from V121, priority, responsible, labels,
-- ticket_relations (for caused_by linking to incidents), attachments. `status` and
-- `request_user` are deliberately omitted — status is auto-managed from problem_status
-- (TicketService), and a Problem record has no requester. `activity_log`/`activity` is also
-- omitted: TicketEditPage.tsx renders the activity log unconditionally outside the template
-- layout, it does not need a layout entry (the `case 'activity_log':` branch in
-- TicketFormRenderer.tsx is unrelated dead code for a different, unused rendering path — not
-- touched here, out of scope).
INSERT INTO ticket_templates (name, description, is_default, created_at, updated_at)
VALUES ('Problem', 'ITIL Problem record — root cause investigation for one or more linked incidents.', 0, NOW(), NOW());

SET @problem_template_id = LAST_INSERT_ID();

UPDATE ticket_templates SET is_internal = 1 WHERE id = @problem_template_id;

INSERT INTO ticket_template_versions (template_id, version_number, is_current, created_at, layout)
VALUES (
    @problem_template_id, 1, 1, NOW(),
    JSON_OBJECT(
        'tabs', JSON_ARRAY(
            JSON_OBJECT(
                'tabKey', 'main',
                'label', 'Main',
                'fields', JSON_ARRAY(
                    JSON_OBJECT('fieldKey','title',            'fieldType','text',            'isSystem',TRUE,  'displayOrder',1,  'defaultValue','', 'width','full'),
                    JSON_OBJECT('fieldKey','description',      'fieldType','text',            'isSystem',TRUE,  'displayOrder',2,  'defaultValue','', 'width','full'),
                    JSON_OBJECT('fieldKey','root_cause',       'fieldType','richtext',         'isSystem',FALSE, 'displayOrder',3,  'defaultValue','', 'width','full'),
                    JSON_OBJECT('fieldKey','workaround',       'fieldType','richtext',         'isSystem',FALSE, 'displayOrder',4,  'defaultValue','', 'width','full'),
                    JSON_OBJECT('fieldKey','known_error',      'fieldType','boolean',          'isSystem',FALSE, 'displayOrder',5,  'defaultValue','false', 'width','half'),
                    JSON_OBJECT('fieldKey','problem_status',   'fieldType','combobox',         'isSystem',FALSE, 'displayOrder',6,  'defaultValue','open', 'width','half',
                                 'fieldOptions', JSON_ARRAY('open','under_investigation','known_error','resolved')),
                    JSON_OBJECT('fieldKey','priority',         'fieldType','combobox',         'isSystem',TRUE,  'displayOrder',7,  'defaultValue','medium', 'width','half',
                                 'fieldOptions', JSON_ARRAY('critical','high','medium','low')),
                    JSON_OBJECT('fieldKey','responsible',      'fieldType','text',            'isSystem',TRUE,  'displayOrder',8,  'defaultValue','', 'width','half'),
                    JSON_OBJECT('fieldKey','labels',           'fieldType','labels',          'isSystem',TRUE,  'displayOrder',9,  'defaultValue','', 'width','full'),
                    JSON_OBJECT('fieldKey','ticket_relations', 'fieldType','ticket_relations','isSystem',FALSE, 'displayOrder',10, 'defaultValue','', 'width','full'),
                    JSON_OBJECT('fieldKey','attachments',      'fieldType','text',            'isSystem',TRUE,  'displayOrder',11, 'defaultValue','', 'width','full')
                )
            )
        )
    )
);
