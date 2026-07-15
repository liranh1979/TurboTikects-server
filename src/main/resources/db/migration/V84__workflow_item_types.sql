-- FEAT-06 Approval Workflows + AI-assisted external integration action items — shared prerequisite.
-- Adds a type discriminator to workflow_items (today every item is the same generic "task" shape).
-- DEFAULT 'task' + nullable everything else so every existing template/ticket keeps working unchanged.

ALTER TABLE workflow_items
    ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'task' AFTER status,
    ADD COLUMN type_config JSON NULL AFTER type,
    ADD COLUMN activation_condition VARCHAR(16) NULL AFTER type_config,
    ADD COLUMN last_error TEXT NULL AFTER field_values;
