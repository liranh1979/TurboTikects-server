-- Ticket Relationships (FEAT-04): typed links between tickets (relates_to,
-- blocks/blocked_by, duplicates/duplicated_by, caused_by/causes,
-- child_of/parent_of). Every link is stored as two rows (the forward type and
-- its auto-created inverse) so reads never need direction-flipping logic.

CREATE TABLE IF NOT EXISTS ticket_relationships (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_ticket_id   BIGINT NOT NULL,
    target_ticket_id   BIGINT NOT NULL,
    relationship_type  VARCHAR(32) NOT NULL,
    created_by         INT NOT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ticket_relationship (source_ticket_id, target_ticket_id, relationship_type),
    CONSTRAINT fk_tr_source FOREIGN KEY (source_ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_tr_target FOREIGN KEY (target_ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);

-- Reuses the existing notification_templates table/pattern (mirrors the
-- ticket_csat_survey seed row in V76__csat_surveys.sql) for the "notify
-- requester" checkbox on the merge dialog.
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject,
  body_template, default_body) VALUES (
'ticket_merge_notify_requester',
'Ticket Merged',
'Sent to the requester when their ticket is merged into another ticket.',
1, 0,
'Your ticket has been merged',
'Your ticket has been merged',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#eff6ff;border-left:4px solid #3b82f6;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#1e3a8a">Your ticket has been merged</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">Your ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — has been merged into another ticket that covers the same issue. All activity has been carried over, and <strong>{{responsible_name}}</strong> will continue working the merged ticket.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Merged into</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{merged_into_id}}</td></tr></table><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#eff6ff;border-left:4px solid #3b82f6;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#1e3a8a">Your ticket has been merged</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">Your ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — has been merged into another ticket that covers the same issue. All activity has been carried over, and <strong>{{responsible_name}}</strong> will continue working the merged ticket.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Merged into</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{merged_into_id}}</td></tr></table><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>'
);

-- type='system': generic UI copy for the Related Tickets panel, link/merge
-- dialogs, and activity-log entries (served live via GET /api/v1/locales/{lang}).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'ticket_relations_panel_title',      'Related Tickets', 'system'),
    ('en', 'ticket_relations_empty',            'No related tickets yet.', 'system'),
    ('en', 'ticket_relations_link_btn',         '+ Link Ticket', 'system'),
    ('en', 'ticket_relations_merge_btn',        'Merge Tickets', 'system'),
    ('en', 'ticket_rel_type_relates_to',        'Relates to', 'system'),
    ('en', 'ticket_rel_type_blocks',            'Blocks', 'system'),
    ('en', 'ticket_rel_type_blocked_by',        'Blocked by', 'system'),
    ('en', 'ticket_rel_type_duplicates',        'Duplicate of', 'system'),
    ('en', 'ticket_rel_type_duplicated_by',     'Duplicated by', 'system'),
    ('en', 'ticket_rel_type_caused_by',         'Caused by', 'system'),
    ('en', 'ticket_rel_type_causes',            'Causes', 'system'),
    ('en', 'ticket_rel_type_child_of',          'Child of', 'system'),
    ('en', 'ticket_rel_type_parent_of',         'Parent of', 'system'),
    ('en', 'ticket_link_dialog_title',          'Link Ticket', 'system'),
    ('en', 'ticket_link_dialog_type_label',     'Relationship Type', 'system'),
    ('en', 'ticket_link_dialog_search_label',   'Search Ticket', 'system'),
    ('en', 'ticket_link_dialog_search_placeholder', 'Search by title or TT-#…', 'system'),
    ('en', 'ticket_link_dialog_link_btn',       'Link', 'system'),
    ('en', 'ticket_link_dialog_no_results',     'No tickets found', 'system'),
    ('en', 'ticket_link_dialog_already_linked', 'These tickets are already linked this way', 'system'),
    ('en', 'ticket_unlink_confirm',             'Remove this relationship?', 'system'),
    ('en', 'ticket_merge_dialog_title',         'Merge Tickets', 'system'),
    ('en', 'ticket_merge_dialog_target_label',  'Merge into', 'system'),
    ('en', 'ticket_merge_dialog_desc',          'All activity, attachments, and comments from {{sourceId}} will be copied to {{targetId}}. {{sourceId}} will be closed as a duplicate.', 'system'),
    ('en', 'ticket_merge_notify_checkbox',      'Notify requester about the merge', 'system'),
    ('en', 'ticket_merge_comment_checkbox',     'Add a comment explaining the merge', 'system'),
    ('en', 'ticket_merge_attachments_checkbox', 'Move attachments to the target ticket', 'system'),
    ('en', 'ticket_merge_confirm_btn',          'Merge Tickets', 'system'),
    ('en', 'ticket_parent_banner_label',        'Parent (Major Incident)', 'system'),
    ('en', 'ticket_parent_banner_child_count',  '{{count}} child tickets', 'system'),
    ('en', 'activity_ticket_linked',            'Linked as "{{relType}}" to {{otherId}}', 'system'),
    ('en', 'activity_ticket_unlinked',          'Removed link to {{otherId}}', 'system'),
    ('en', 'activity_ticket_merged',            'This ticket was merged into {{targetId}}', 'system'),
    ('en', 'activity_ticket_merged_from',       'Merged {{sourceId}} into this ticket ({{attachmentsMoved}} attachments, {{activityRowsCopied}} activity entries copied)', 'system'),
    ('en', 'activity_ticket_merge_note',        'This ticket was merged with {{otherId}}', 'system');
