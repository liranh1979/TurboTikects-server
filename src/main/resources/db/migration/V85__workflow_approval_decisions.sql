-- FEAT-06 Approval Workflows, Phase 1: one row per approval level walked at runtime.
-- Level *definitions* (approver, timeout) live in workflow_items.type_config (Phase 0); this table
-- is the runtime state of walking through them.

CREATE TABLE IF NOT EXISTS workflow_approval_decisions (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_item_id      BIGINT NOT NULL,
    level_order           INT NOT NULL,
    approver_user_id      INT NULL,
    approver_group_id     INT NULL,
    decision              VARCHAR(16) NOT NULL DEFAULT 'pending',   -- pending | approved | rejected | escalated
    decided_at            DATETIME NULL,
    decided_by_user_id    INT NULL,
    rejection_reason      TEXT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_workflow_approval_level (workflow_item_id, level_order),
    CONSTRAINT fk_approval_decision_item FOREIGN KEY (workflow_item_id) REFERENCES workflow_items(id) ON DELETE CASCADE
);
