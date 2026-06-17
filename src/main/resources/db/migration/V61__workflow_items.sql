-- Runtime workflow items: one row per template node per ticket
CREATE TABLE workflow_items (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    ticket_id           BIGINT       NOT NULL,
    template_node_id    VARCHAR(64)  NOT NULL,
    parent_item_id      BIGINT       NULL,
    title               VARCHAR(512) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'pending',
    assigned_user_id    INT          NULL,
    assigned_group_id   INT          NULL,
    field_values        JSON         NULL,
    display_order       INT          NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_workflow_ticket_node (ticket_id, template_node_id),
    FOREIGN KEY (ticket_id)      REFERENCES tickets(id)        ON DELETE CASCADE,
    FOREIGN KEY (parent_item_id) REFERENCES workflow_items(id) ON DELETE SET NULL
);

-- Notification template for when a workflow item becomes active
INSERT IGNORE INTO notification_templates (
    notification_type, display_name, description, is_enabled, is_admin_facing,
    subject_template, default_subject, body_template, default_body
) VALUES (
    'workflow_item_activated',
    'Workflow Item Activated',
    'Sent when a workflow item becomes active and has an assignee.',
    1, 1,
    'You have a new workflow task: {{workflow_title}} [TT-{{ticket_id}}]',
    'You have a new workflow task: {{workflow_title}} [TT-{{ticket_id}}]',
    '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#eef2ff;border-left:4px solid #4f46e5;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#3730a3">New workflow task assigned to you</p></div><p style="margin:0 0 16px;color:#475569">You have been assigned to a workflow task that is now active.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Task</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{workflow_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Ticket</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr></table><p style="margin:0 0 8px;color:#475569">View the ticket and manage your task:</p><p style="margin:0 0 20px"><a href="{{workflow_url}}" style="color:#4f46e5">{{workflow_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
    '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#eef2ff;border-left:4px solid #4f46e5;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#3730a3">New workflow task assigned to you</p></div><p style="margin:0 0 16px;color:#475569">You have been assigned to a workflow task that is now active.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Task</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{workflow_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Ticket</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr></table><p style="margin:0 0 8px;color:#475569">View the ticket and manage your task:</p><p style="margin:0 0 20px"><a href="{{workflow_url}}" style="color:#4f46e5">{{workflow_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>'
);

-- Translation keys for workflow status values and UI labels
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'wf_status_pending',          'Not Started',          'system'),
    ('en', 'wf_status_in_progress',      'In Progress',          'system'),
    ('en', 'wf_status_blocked',          'Blocked',              'system'),
    ('en', 'wf_status_done',             'Done',                 'system'),
    ('en', 'wf_status_canceled',         'Canceled',             'system'),
    ('en', 'wf_status_suspended',        'Suspended',            'system'),
    ('en', 'workflow_field_type_label',  'Workflow',             'system'),
    ('en', 'my_workflow_items_title',    'My Workflow Tasks',    'system');
