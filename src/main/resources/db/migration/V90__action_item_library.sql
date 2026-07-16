-- Reusable, cross-template catalog of workflow action items (Simple/task-type items built by hand,
-- and AI-built external_api/mcp_tool items from the AI Workflow Builder) — see WorkflowDesignerModal's
-- new "Add from Library" picker. Entries are copied (not referenced) into a template's workflow
-- nodes on add, so editing a library entry or a template's copy never cross-affects the other.
CREATE TABLE IF NOT EXISTS action_item_library (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    type          VARCHAR(32)  NOT NULL,
    type_config   JSON         NULL,
    source        VARCHAR(16)  NOT NULL DEFAULT 'manual',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
