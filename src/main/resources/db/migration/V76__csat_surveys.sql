-- CSAT (Customer Satisfaction) surveys: sent to the requester when a ticket
-- is resolved (transitions to 'completed' or 'closed'). One row per ticket —
-- the same row is created when the survey is sent (token issued) and filled
-- in when the requester responds.

CREATE TABLE IF NOT EXISTS ticket_csat (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL UNIQUE,
    token VARCHAR(36) NOT NULL UNIQUE,
    score TINYINT NULL,
    comment TEXT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP NULL,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);

-- Reuses the existing notification_templates table/pattern (mirrors the
-- new_ticket_requester seed row in V48__notification_system.sql).
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject,
  body_template, default_body) VALUES (
'ticket_csat_survey',
'CSAT Survey',
'Sent to the request user when their ticket is resolved, asking them to rate their support experience.',
1, 0,
'How did we do? ⭐',
'How did we do? ⭐',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fefce8;border-left:4px solid #eab308;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#854d0e">How did we do? ⭐</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">Your support ticket has been resolved. Please take a moment to rate our service — your feedback helps us improve.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Subject</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Resolved by</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{responsible_name}}</td></tr></table><p style="margin:0 0 20px"><a href="{{csat_survey_url}}" style="display:inline-block;background:#eab308;color:#1a202c;font-weight:700;padding:10px 22px;border-radius:8px;text-decoration:none">Rate Your Experience</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>',
'<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fefce8;border-left:4px solid #eab308;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#854d0e">How did we do? ⭐</p></div><p style="margin:0 0 6px">Hello <strong>{{requester_name}}</strong>,</p><p style="margin:0 0 16px;color:#475569">Your support ticket has been resolved. Please take a moment to rate our service — your feedback helps us improve.</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:140px">Ticket ID</td><td style="padding:8px 12px;border:1px solid #e2e8f0">TT-{{ticket_id}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Subject</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Resolved by</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{responsible_name}}</td></tr></table><p style="margin:0 0 20px"><a href="{{csat_survey_url}}" style="display:inline-block;background:#eab308;color:#1a202c;font-weight:700;padding:10px 22px;border-radius:8px;text-decoration:none">Rate Your Experience</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system. Please do not reply directly to this email.</p></div>'
);

-- type='system': generic UI copy for the public survey page + dashboard section
-- (served live via GET /api/v1/locales/{lang}, which only reads type='system' rows)
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'csat_survey_question', 'Overall, how satisfied are you with this support experience?', 'system'),
    ('en', 'csat_survey_comment_prompt', 'Any additional comments? (optional)', 'system'),
    ('en', 'csat_survey_submit', 'Submit Feedback', 'system'),
    ('en', 'csat_survey_thanks', 'Thanks for your feedback!', 'system'),
    ('en', 'csat_survey_already_responded', 'You have already responded to this survey. Thanks again!', 'system'),
    ('en', 'csat_survey_expired', 'This survey link has expired.', 'system'),
    ('en', 'dashboard_csat_section_title', 'Customer Satisfaction', 'system'),
    ('en', 'dashboard_csat_avg_label', 'Avg CSAT (30d)', 'system'),
    ('en', 'dashboard_csat_response_rate_label', 'Response Rate', 'system'),
    ('en', 'dashboard_csat_low_score_label', 'Low Score (1–2★)', 'system'),
    ('en', 'dashboard_csat_low_score_table_title', 'Low Score Tickets — Need Follow-Up', 'system'),
    ('en', 'dashboard_csat_ai_title', 'CSAT Analysis', 'system');
