-- FEAT-14: Announcements & Broadcast + admin-manageable Alert Types.
-- Alert types are a real admin-managed entity (not a fixed enum): 4 built-in types
-- (is_system=1, undeletable) plus admin-addable custom ones. Each type's display name lives
-- in dynamic_translations under its own 'alert_types' type category, keyed by type_key —
-- same convention as 'ticket_fields'/'label' (translation_key = the raw entity key, no prefix).
-- See V2/feature-14-announcements.html for the Part A mock/spec.

CREATE TABLE IF NOT EXISTS alert_types (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_key       VARCHAR(64)  NOT NULL UNIQUE,
    color          VARCHAR(16)  NOT NULL DEFAULT 'info',
    icon           VARCHAR(8)   NULL,
    is_system      TINYINT(1)   NOT NULL DEFAULT 0,
    display_order  INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT IGNORE INTO alert_types (type_key, color, icon, is_system, display_order) VALUES
    ('outage',        'critical', '🔴', 1, 1),
    ('degraded',      'high',     '🟠', 1, 2),
    ('informational', 'info',     '🔵', 1, 3),
    ('resolved',      'low',      '🟢', 1, 4);

CREATE TABLE IF NOT EXISTS announcements (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    severity                VARCHAR(64)  NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    message                 TEXT NOT NULL,
    show_on_portal          TINYINT(1) NOT NULL DEFAULT 1,
    show_on_ticket_create   TINYINT(1) NOT NULL DEFAULT 0,
    show_on_agent_dashboard TINYINT(1) NOT NULL DEFAULT 0,
    is_active               TINYINT(1) NOT NULL DEFAULT 1,
    broadcast_email         TINYINT(1) NOT NULL DEFAULT 0,
    broadcast_target        VARCHAR(16) NULL,
    broadcast_group_id      INT NULL,
    created_by              INT NULL,
    resolved_at             TIMESTAMP NULL,
    expires_at              TIMESTAMP NULL,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_announcements_active ON announcements (is_active, severity);

INSERT IGNORE INTO permissions (permission_key, display_order) VALUES ('MANAGE_ANNOUNCEMENTS', 11);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'outage',        'Outage',        'alert_types'),
    ('en', 'degraded',      'Degraded',      'alert_types'),
    ('en', 'informational', 'Informational', 'alert_types'),
    ('en', 'resolved',      'Resolved',      'alert_types'),

    ('en', 'permission_manage_announcements_name', 'Announcements', 'system'),
    ('en', 'permission_manage_announcements_desc', 'Can compose and manage announcement banners and broadcasts', 'system'),
    ('en', 'announcements_nav_item',      'Announcements', 'system'),
    ('en', 'announcements_card_label',    'Announcements', 'system'),
    ('en', 'announcements_card_subtitle', 'Post status banners and broadcast email updates', 'system'),
    ('en', 'announcements_page_title',    'Announcements', 'system'),
    ('en', 'announcements_add_btn',       '+ New Announcement', 'system'),
    ('en', 'announcements_list_empty',    'No announcements yet.', 'system'),
    ('en', 'announcements_col_severity',  'Alert Type', 'system'),
    ('en', 'announcements_col_title',     'Title', 'system'),
    ('en', 'announcements_col_status',    'Status', 'system'),
    ('en', 'announcements_col_created',   'Posted', 'system'),
    ('en', 'announcements_col_actions',   'Actions', 'system'),
    ('en', 'announcements_form_title_new',  'New Announcement', 'system'),
    ('en', 'announcements_form_title_edit', 'Edit Announcement', 'system'),
    ('en', 'announcements_form_severity_label', 'Alert Type', 'system'),
    ('en', 'announcements_form_title_label',    'Title', 'system'),
    ('en', 'announcements_form_message_label',  'Message', 'system'),
    ('en', 'announcements_form_show_on_label',  'Show On', 'system'),
    ('en', 'announcements_show_on_portal',          'End-User Portal banner', 'system'),
    ('en', 'announcements_show_on_ticket_create',   'Ticket creation page', 'system'),
    ('en', 'announcements_show_on_agent_dashboard', 'Agent dashboard', 'system'),
    ('en', 'announcements_form_broadcast_label', 'Send Email Broadcast To', 'system'),
    ('en', 'announcements_broadcast_all',        'All users', 'system'),
    ('en', 'announcements_broadcast_group',      'Specific group', 'system'),
    ('en', 'announcements_post_btn',    'Post Announcement', 'system'),
    ('en', 'announcements_preview_btn', 'Preview', 'system'),
    ('en', 'announcements_resolve_btn', 'Mark Resolved', 'system'),
    ('en', 'announcements_delete_confirm', 'Delete this announcement? This cannot be undone.', 'system'),
    ('en', 'announcements_posted_by', 'Posted by', 'system'),

    ('en', 'field_group_alerts',       'Alert Types', 'system'),
    ('en', 'alert_types_defined_fields', 'Types used for announcement severity banners', 'system'),
    ('en', 'alert_type_key_label',     'Key', 'system'),
    ('en', 'alert_type_color_label',   'Color', 'system'),
    ('en', 'alert_type_icon_label',    'Icon (emoji)', 'system'),
    ('en', 'alert_type_create_btn',    '+ Add Alert Type', 'system'),
    ('en', 'alert_type_delete_confirm','Delete this alert type? This cannot be undone.', 'system'),
    ('en', 'alert_type_system_locked', 'Built-in — cannot be deleted', 'system'),
    ('en', 'no_alert_types',           'No alert types defined.', 'system');
