-- Permission
INSERT INTO permissions (permission_key, display_order) VALUES ('MANAGE_NOTIFICATIONS', 9);

-- Notification templates
CREATE TABLE notification_templates (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  notification_type VARCHAR(64) NOT NULL,
  display_name      VARCHAR(100) NOT NULL,
  description       TEXT,
  is_enabled        TINYINT(1) NOT NULL DEFAULT 1,
  subject_template  VARCHAR(500) NOT NULL,
  body_template     TEXT NOT NULL,
  default_subject   VARCHAR(500) NOT NULL,
  default_body      TEXT NOT NULL,
  is_admin_facing   TINYINT(1) NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_notification_type (notification_type)
);

-- Seed default templates

-- 1. New ticket → request user
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject,
  body_template, default_body) VALUES (
'new_ticket_requester',
'New Ticket – Requester Confirmation',
'Sent to the request user when a new ticket is created. Skipped if the user created the ticket themselves manually.',
1, 0,
'[TT-{{ticket_id}}] Your support request has been received',
'[TT-{{ticket_id}}] Your support request has been received',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#eff6ff;border-left:4px solid #3b82f6;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#1e40af">Your request has been received ✓</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">Thank you for contacting support. Your ticket has been created and our team will review it shortly.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Subject</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr></table><p style="margin:0 0 8px;color:#475569">You can view your ticket at any time using the link below:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#3b82f6">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#eff6ff;border-left:4px solid #3b82f6;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#1e40af">Your request has been received ✓</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">Thank you for contacting support. Your ticket has been created and our team will review it shortly.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Subject</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr></table><p style="margin:0 0 8px;color:#475569">You can view your ticket at any time using the link below:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#3b82f6">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>'
);

-- 2. New ticket → responsible admin/group
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject,
  body_template, default_body) VALUES (
'new_ticket_responsible',
'New Ticket – Responsible Notification',
'Sent to the responsible admin (or all members of the responsible group) when a ticket is assigned.',
1, 1,
'[TT-{{ticket_id}}] New ticket assigned to you: {{ticket_title}}',
'[TT-{{ticket_id}}] New ticket assigned to you: {{ticket_title}}',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fdf4ff;border-left:4px solid #a855f7;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#7e22ce">New ticket assigned to you</p></div><p style="margin:0 0 6px">Hello <strong>{{responsible_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">A new support ticket has been assigned to you and requires your attention.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Title</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Requester</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{requester_name}}</td></tr></table><p style="margin:0 0 8px;color:#475569">View and manage this ticket:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#a855f7">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fdf4ff;border-left:4px solid #a855f7;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#7e22ce">New ticket assigned to you</p></div><p style="margin:0 0 6px">Hello <strong>{{responsible_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">A new support ticket has been assigned to you and requires your attention.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Title</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Requester</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{requester_name}}</td></tr></table><p style="margin:0 0 8px;color:#475569">View and manage this ticket:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#a855f7">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>'
);

-- 3. Ticket updated → request user
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject,
  body_template, default_body) VALUES (
'ticket_updated_requester',
'Ticket Updated – Requester Notification',
'Sent to the request user when their ticket is updated (status change, new note, AI solution, etc.). Skipped if the requester initiated the change.',
1, 0,
'[TT-{{ticket_id}}] Update on your support request: {{ticket_title}}',
'[TT-{{ticket_id}}] Update on your support request: {{ticket_title}}',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fff7ed;border-left:4px solid #f97316;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#c2410c">Your ticket has been updated</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">There has been an update to your support ticket. Please review the latest changes below.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Subject</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Current Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr></table><p style="margin:0 0 8px;color:#475569">View the full ticket and activity history:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#f97316">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fff7ed;border-left:4px solid #f97316;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#c2410c">Your ticket has been updated</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">There has been an update to your support ticket. Please review the latest changes below.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Subject</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Current Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr></table><p style="margin:0 0 8px;color:#475569">View the full ticket and activity history:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#f97316">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>'
);

-- 4. Ticket updated → responsible admin
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject,
  body_template, default_body) VALUES (
'ticket_updated_admin',
'Ticket Updated – Admin Notification',
'Sent to the responsible admin when a ticket they manage is updated. Skipped if the admin initiated the change.',
1, 1,
'[TT-{{ticket_id}}] Ticket updated: {{ticket_title}}',
'[TT-{{ticket_id}}] Ticket updated: {{ticket_title}}',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#f0fdf4;border-left:4px solid #22c55e;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#15803d">Ticket activity update</p></div><p style="margin:0 0 6px">Hello <strong>{{responsible_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">A ticket you are responsible for has been updated.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Title</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Requester</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{requester_name}}</td></tr></table><p style="margin:0 0 8px;color:#475569">Review the full ticket:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#22c55e">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#f0fdf4;border-left:4px solid #22c55e;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#15803d">Ticket activity update</p></div><p style="margin:0 0 6px">Hello <strong>{{responsible_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">A ticket you are responsible for has been updated.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Title</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Status</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_status}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Requester</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{requester_name}}</td></tr></table><p style="margin:0 0 8px;color:#475569">Review the full ticket:</p><p style="margin:0 0 20px"><a href="{{ticket_url}}" style="color:#22c55e">{{ticket_url}}</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>'
);

-- Translations
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'permission_manage_notifications_name', 'Manage Notifications', 'system'),
('en', 'permission_manage_notifications_desc', 'Configure and customize notification templates', 'system'),
('en', 'settings_notification_manager', 'Notifications', 'system'),
('en', 'notification_manager_desc', 'Manage email notification templates for ticket events', 'system'),
('en', 'notification_type_new_ticket_requester', 'New Ticket – Requester', 'system'),
('en', 'notification_type_new_ticket_responsible', 'New Ticket – Responsible', 'system'),
('en', 'notification_type_ticket_updated_requester', 'Ticket Updated – Requester', 'system'),
('en', 'notification_type_ticket_updated_admin', 'Ticket Updated – Admin', 'system'),
('en', 'notification_enabled_label', 'Enable this notification', 'system'),
('en', 'notification_subject_label', 'Email Subject', 'system'),
('en', 'notification_body_label', 'Email Body', 'system'),
('en', 'notification_rollback_btn', 'Restore Default', 'system'),
('en', 'notification_save_btn', 'Save Template', 'system'),
('en', 'notification_saved_msg', 'Template saved successfully', 'system'),
('en', 'notification_restored_msg', 'Template restored to default', 'system'),
('en', 'notification_insert_field_label', 'Insert field', 'system'),
('en', 'notification_available_fields', 'Available placeholders', 'system'),
('en', 'notification_admin_only_hint', 'Admin-facing templates can include all ticket fields', 'system');
