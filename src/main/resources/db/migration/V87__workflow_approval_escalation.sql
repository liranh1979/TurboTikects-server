-- FEAT-06 Approval Workflows, Phase 3: timeout escalation.
-- V85's UNIQUE KEY(workflow_item_id, level_order) assumed exactly one decision row per level, but
-- escalation needs a SECOND row at the same level_order (the original approver's row gets marked
-- 'escalated' as a terminal/historical record, and a new 'pending' row is created for the
-- escalate-to approver) — drop it; "the current one" is whichever row has decision='pending'
-- (queried via findByWorkflowItemIdAndDecision), ordering by id/created_at for history display.
--
-- MySQL was silently relying on uq_workflow_approval_level as fk_approval_decision_item's
-- supporting index (workflow_item_id is its leftmost column) — dropping it directly fails with
-- "Cannot drop index ... needed in a foreign key constraint" (error 1553). Add a plain,
-- non-unique index covering the same leftmost column first so the FK has something to keep.
CREATE INDEX idx_workflow_approval_decisions_item ON workflow_approval_decisions (workflow_item_id);
ALTER TABLE workflow_approval_decisions DROP INDEX uq_workflow_approval_level;
