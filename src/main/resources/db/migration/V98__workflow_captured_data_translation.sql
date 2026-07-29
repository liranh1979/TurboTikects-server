-- WorkflowTreePanel now shows a read-only "Captured Data" section for external_api/mcp_tool
-- action items' captured this.<key> field values, which previously had no display anywhere in
-- the UI (only Simple/task-type items' own mini-fields were rendered, via SimpleItemFieldsForm).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'workflow_captured_data_label', 'Captured Data', 'system');
