-- Timer field support: field_config column for per-field-type settings,
-- admin_notifications table for in-app alerts, timer_alert notification template.

-- Per-field-type configuration (timer: alert_threshold_percent, default_duration, etc.)
ALTER TABLE field_definitions
    ADD COLUMN field_config JSON NULL;

-- In-app admin notification inbox
CREATE TABLE admin_notifications (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    ticket_id         BIGINT       NOT NULL,
    field_key         VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50)  NOT NULL,   -- 'TIMER_ALERT'
    message           TEXT         NULL,
    is_read           TINYINT(1)   NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_admin_notif (ticket_id, field_key, notification_type),
    CONSTRAINT fk_admin_notif_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);

-- Email notification template for timer alerts
INSERT IGNORE INTO notification_templates
    (notification_type, display_name, description, is_enabled, is_admin_facing,
     subject_template, default_subject,
     body_template,    default_body,
     created_at, updated_at)
VALUES (
    'timer_alert',
    'Timer Alert',
    'Sent to the responsible admin when a ticket timer is approaching its deadline.',
    1, 1,
    '[TT-{{ticketId}}] Timer expiring: {{ticketTitle}}',
    '[TT-{{ticketId}}] Timer expiring: {{ticketTitle}}',
    '<p>Ticket <strong>TT-{{ticketId}}</strong> &ldquo;{{ticketTitle}}&rdquo; has a timer field that is approaching its deadline.</p><p><a href="{{ticketUrl}}">View ticket</a></p>',
    '<p>Ticket <strong>TT-{{ticketId}}</strong> &ldquo;{{ticketTitle}}&rdquo; has a timer field that is approaching its deadline.</p><p><a href="{{ticketUrl}}">View ticket</a></p>',
    NOW(), NOW()
);

-- Translation keys for timer field type UI
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'field_type_timer',              'Timer',                                           'system'),
('en', 'timer_duration_label',          'Duration',                                        'system'),
('en', 'timer_unit_hours',              'Hours',                                           'system'),
('en', 'timer_unit_days',               'Days',                                            'system'),
('en', 'timer_target_label',            'Target deadline',                                 'system'),
('en', 'timer_remaining',               'remaining',                                       'system'),
('en', 'timer_overdue',                 'overdue',                                         'system'),
('en', 'timer_not_set',                 'Not set',                                         'system'),
('en', 'timer_alert_threshold',         'Alert when % remaining',                          'system'),
('en', 'admin_inbox_title',             'Notifications',                                   'system'),
('en', 'admin_inbox_empty',             'No notifications',                                'system'),
('en', 'admin_inbox_mark_all_read',     'Mark all read',                                   'system'),
('en', 'admin_inbox_timer_alert',       'Timer expiring on ticket TT-{{id}}: {{title}}',   'system');
