-- FEAT-06 Approval Workflows, Phase 2: one-click email approve/reject tokens.

CREATE TABLE IF NOT EXISTS workflow_approval_tokens (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_approval_decision_id   BIGINT NOT NULL,
    token                           VARCHAR(36) NOT NULL,
    expires_at                      DATETIME NOT NULL,
    used_at                         DATETIME NULL,
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_workflow_approval_token (token),
    CONSTRAINT fk_approval_token_decision FOREIGN KEY (workflow_approval_decision_id)
        REFERENCES workflow_approval_decisions(id) ON DELETE CASCADE
);

-- Reuses the existing notification_templates + EmailSenderService pipeline as-is (same pattern as
-- CSAT/SLA emails) — {{approve_url}}/{{reject_url}} carry the token, landing on the same public
-- WorkflowApprovalPage either way (which action is pre-selected there just reflects which link the
-- approver clicked); the actual decision only records once they confirm on that page, not on GET,
-- so an email-security-scanner prefetch of the link can't silently record a decision no human made.
INSERT INTO notification_templates (notification_type, display_name, description, is_enabled, is_admin_facing,
  subject_template, default_subject, body_template, default_body) VALUES
('workflow_approval_request',
 'Workflow Approval Request', 'Sent to an approver when a workflow approval step activates.',
 1, 1,
 'Approval Required — TT-{{ticket_id}}: {{ticket_title}}',
 'Approval Required — TT-{{ticket_id}}: {{ticket_title}}',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fefce8;border-left:4px solid #eab308;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#854d0e">🔔 Approval Required</p></div><p style="margin:0 0 16px">Your approval is required for the following request:</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Request</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Submitted by</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{requester_name}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Step</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{item_title}}</td></tr></table><p style="margin:0 0 20px">Please review and approve or reject below.</p><a href="{{approve_url}}" style="display:inline-block;background:#22c55e;color:#fff;text-decoration:none;border-radius:6px;padding:.65rem 1.5rem;font-weight:700;margin-right:10px">✓ Approve</a><a href="{{reject_url}}" style="display:inline-block;background:#ef4444;color:#fff;text-decoration:none;border-radius:6px;padding:.65rem 1.5rem;font-weight:700">✗ Reject</a><p style="margin-top:16px"><a href="{{ticket_url}}" style="color:#3b82f6;font-size:13px">View full ticket details →</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>',
 '<div style="font-family:Arial,sans-serif;max-width:600px;color:#1a202c"><div style="background:#fefce8;border-left:4px solid #eab308;padding:14px 18px;border-radius:0 8px 8px 0;margin-bottom:20px"><p style="margin:0;font-size:16px;font-weight:700;color:#854d0e">🔔 Approval Required</p></div><p style="margin:0 0 16px">Your approval is required for the following request:</p><table style="width:100%;border-collapse:collapse;margin-bottom:20px"><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600;width:160px">Request</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{ticket_title}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Submitted by</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{requester_name}}</td></tr><tr><td style="padding:8px 12px;background:#f8fafc;border:1px solid #e2e8f0;font-weight:600">Step</td><td style="padding:8px 12px;border:1px solid #e2e8f0">{{item_title}}</td></tr></table><p style="margin:0 0 20px">Please review and approve or reject below.</p><a href="{{approve_url}}" style="display:inline-block;background:#22c55e;color:#fff;text-decoration:none;border-radius:6px;padding:.65rem 1.5rem;font-weight:700;margin-right:10px">✓ Approve</a><a href="{{reject_url}}" style="display:inline-block;background:#ef4444;color:#fff;text-decoration:none;border-radius:6px;padding:.65rem 1.5rem;font-weight:700">✗ Reject</a><p style="margin-top:16px"><a href="{{ticket_url}}" style="color:#3b82f6;font-size:13px">View full ticket details →</a></p><hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0"/><p style="color:#94a3b8;font-size:12px;margin:0">This message was sent via the TurboTickets helpdesk system.</p></div>');
