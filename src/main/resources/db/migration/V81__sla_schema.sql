-- FEAT-02: SLA Management Engine — core schema (Phase 0 of a multi-phase build).
-- A real SLA engine, distinct from the general-purpose `timer` field type: policy-driven
-- (keyed by ticket priority, not manually entered per ticket), tracks pause/resume,
-- and drives escalation. See V2/feature-02-sla.html for the full spec.

CREATE TABLE IF NOT EXISTS sla_policies (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    priority                VARCHAR(16) NOT NULL,
    template_id             BIGINT NULL,              -- NULL = applies to all templates at this priority
    first_response_minutes  INT NOT NULL,
    resolution_minutes      INT NOT NULL,
    is_business_hours       TINYINT(1) NOT NULL DEFAULT 1,
    is_active               TINYINT(1) NOT NULL DEFAULT 1,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_sla_policy (priority, template_id),
    CONSTRAINT fk_sla_policy_template FOREIGN KEY (template_id) REFERENCES ticket_templates(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sla_escalation_steps (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    sla_policy_id      BIGINT NOT NULL,
    step_order         INT NOT NULL,
    -- PERCENT_OF_RESPONSE | PERCENT_OF_RESOLUTION | AT_BREACH | AFTER_BREACH_MINUTES
    trigger_type       VARCHAR(32) NOT NULL,
    trigger_value      DECIMAL(8,2) NULL,      -- percent or minutes; NULL for AT_BREACH
    -- NOTIFY_EMAIL | NOTIFY_INAPP | REASSIGN | WEBHOOK
    action_type        VARCHAR(32) NOT NULL,
    -- Explicit target picked by an admin per step (no manager/reporting-line concept exists in
    -- this schema — see plan notes). If both are NULL, the executor falls back to notifying the
    -- ticket's current responsible (assigned) user, so a policy is useful before any target is set.
    target_user_id     INT NULL,
    target_group_id    INT NULL,
    webhook_url        VARCHAR(500) NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_step_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policies(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ticket_sla_state (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id                   BIGINT NOT NULL UNIQUE,
    sla_policy_id                BIGINT NULL,     -- resolved at ticket creation from priority; NULL if no matching policy
    first_response_target_at    TIMESTAMP NULL,
    first_response_at           TIMESTAMP NULL,
    first_response_breached     TINYINT(1) NOT NULL DEFAULT 0,
    resolution_target_at        TIMESTAMP NULL,
    resolution_at                TIMESTAMP NULL,
    resolution_breached         TINYINT(1) NOT NULL DEFAULT 0,
    paused_at                   TIMESTAMP NULL,   -- non-null while paused (status = waiting)
    total_paused_seconds        BIGINT NOT NULL DEFAULT 0,
    ai_breach_risk_score        INT NULL,
    ai_risk_reason               TEXT NULL,
    ai_last_scored_at           TIMESTAMP NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sla_state_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_sla_state_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policies(id)
);

CREATE TABLE IF NOT EXISTS ticket_sla_escalations_fired (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id               BIGINT NOT NULL,
    sla_escalation_step_id  BIGINT NOT NULL,
    fired_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_fired (ticket_id, sla_escalation_step_id),
    CONSTRAINT fk_fired_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_fired_step FOREIGN KEY (sla_escalation_step_id) REFERENCES sla_escalation_steps(id) ON DELETE CASCADE
);

-- Default policy set matching the spec's Mock 1 default template exactly.
-- Low's resolution (3 days) is seeded as 1440 minutes = 24 business hours (8hr/day convention
-- already used elsewhere in this app for "days" duration units), not a literal 72 calendar hours.
INSERT INTO sla_policies (priority, template_id, first_response_minutes, resolution_minutes, is_business_hours, is_active) VALUES
    ('critical', NULL, 15,  60,   0, 1),
    ('high',     NULL, 60,  240,  1, 1),
    ('medium',   NULL, 240, 480,  1, 1),
    ('low',      NULL, 480, 1440, 1, 1);

-- One "notify at X%" step per default policy, matching Mock 1's "Notify At" column. No explicit
-- target (see comment above — falls back to the ticket's assigned agent until an admin configures
-- a specific escalation target via Settings → SLA).
INSERT INTO sla_escalation_steps (sla_policy_id, step_order, trigger_type, trigger_value, action_type)
SELECT id, 1, 'PERCENT_OF_RESOLUTION', 80, 'NOTIFY_EMAIL' FROM sla_policies WHERE priority = 'critical' AND template_id IS NULL;
INSERT INTO sla_escalation_steps (sla_policy_id, step_order, trigger_type, trigger_value, action_type)
SELECT id, 1, 'PERCENT_OF_RESOLUTION', 75, 'NOTIFY_EMAIL' FROM sla_policies WHERE priority = 'high' AND template_id IS NULL;
INSERT INTO sla_escalation_steps (sla_policy_id, step_order, trigger_type, trigger_value, action_type)
SELECT id, 1, 'PERCENT_OF_RESOLUTION', 80, 'NOTIFY_EMAIL' FROM sla_policies WHERE priority = 'medium' AND template_id IS NULL;
INSERT INTO sla_escalation_steps (sla_policy_id, step_order, trigger_type, trigger_value, action_type)
SELECT id, 1, 'PERCENT_OF_RESOLUTION', 90, 'NOTIFY_EMAIL' FROM sla_policies WHERE priority = 'low' AND template_id IS NULL;

-- Notification templates for the escalation executor (mirrors the existing pattern — e.g.
-- V76__csat_surveys.sql, V79__ticket_relationships.sql — reusing the existing
-- notification_templates + EmailSenderService pipeline as-is).
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject, body_template, default_body) VALUES
('sla_warning',
 'SLA Warning', 'Sent to the assigned agent (or an escalation step target) when a ticket approaches its SLA resolution deadline.',
 1, 1,
 'SLA Warning: TT-{{ticket_id}} approaching resolution deadline',
 'SLA Warning: TT-{{ticket_id}} approaching resolution deadline',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fefce8;border-left:4px solid #eab308;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#854d0e">⚠ SLA Warning</p></div><p style="margin:0 0 16px">Ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — is approaching its resolution SLA deadline.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Priority</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{priority}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Time elapsed</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{percent_used}}% of resolution SLA used</td></tr></table><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fefce8;border-left:4px solid #eab308;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#854d0e">⚠ SLA Warning</p></div><p style="margin:0 0 16px">Ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — is approaching its resolution SLA deadline.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Priority</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{priority}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Time elapsed</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{percent_used}}% of resolution SLA used</td></tr></table><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>'),
('sla_breach',
 'SLA Breach', 'Sent when a ticket breaches its resolution SLA.',
 1, 1,
 'SLA BREACHED: TT-{{ticket_id}}',
 'SLA BREACHED: TT-{{ticket_id}}',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fef2f2;border-left:4px solid #ef4444;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#991b1b">🚨 SLA Breached</p></div><p style="margin:0 0 16px">Ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — has breached its resolution SLA.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Priority</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{priority}}</td></tr></table><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fef2f2;border-left:4px solid #ef4444;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#991b1b">🚨 SLA Breached</p></div><p style="margin:0 0 16px">Ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — has breached its resolution SLA.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Priority</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{priority}}</td></tr></table><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>'),
('sla_escalation',
 'SLA Escalation', 'Sent when an SLA escalation step fires (e.g. reassignment or a later ladder step after breach).',
 1, 1,
 'SLA Escalation: TT-{{ticket_id}}',
 'SLA Escalation: TT-{{ticket_id}}',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fef2f2;border-left:4px solid #ef4444;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#991b1b">Escalation: TT-{{ticket_id}}</p></div><p style="margin:0 0 16px">Ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — has been escalated to you.</p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fef2f2;border-left:4px solid #ef4444;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#991b1b">Escalation: TT-{{ticket_id}}</p></div><p style="margin:0 0 16px">Ticket <strong>TT-{{ticket_id}}</strong> — {{ticket_title}} — has been escalated to you.</p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>');

-- type='system': generic UI copy for the SLA policy/escalation admin UI and countdown widget.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'sla_settings_nav_item',       'SLA', 'system'),
    ('en', 'sla_policies_title',          'SLA Policies', 'system'),
    ('en', 'sla_add_policy_btn',          '+ Add SLA Policy', 'system'),
    ('en', 'sla_auto_pause_note',         'SLA auto-pauses when status = Waiting', 'system'),
    ('en', 'sla_col_priority',            'Priority', 'system'),
    ('en', 'sla_col_first_response',      'First Response', 'system'),
    ('en', 'sla_col_resolution',          'Resolution Time', 'system'),
    ('en', 'sla_col_business_hours',      'Business Hours', 'system'),
    ('en', 'sla_col_escalate_to',         'Escalate To', 'system'),
    ('en', 'sla_col_notify_at',           'Notify At', 'system'),
    ('en', 'sla_first_response_label',    'First Response SLA', 'system'),
    ('en', 'sla_resolution_label',        'Resolution SLA', 'system'),
    ('en', 'sla_breached_label',          'SLA BREACHED', 'system'),
    ('en', 'sla_paused_label',            'SLA Paused', 'system'),
    ('en', 'sla_time_used_label',         'Resolution time used', 'system'),
    ('en', 'sla_escalation_ladder_title', 'Escalation Ladder', 'system'),
    ('en', 'sla_add_step_btn',            '+ Add Step', 'system'),
    ('en', 'sla_ai_risk_label',           'AI Breach Risk', 'system');
