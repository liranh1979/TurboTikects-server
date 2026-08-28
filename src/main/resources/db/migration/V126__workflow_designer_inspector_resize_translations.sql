-- Workflow Designer's Node Inspector panel — real usability gap found live: the fixed 336px
-- panel truncated field values badly once an MCP tool call's arguments/response-captures/
-- response-mappings sections were all populated. Added a drag-to-resize handle plus a one-click
-- widen/narrow toggle button (see WorkflowDesignerModal.tsx).
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'workflow_inspector_widen_btn',   'Widen panel',    'system'),
('en', 'workflow_inspector_narrow_btn',  'Narrow panel',   'system'),
('en', 'workflow_inspector_resize_hint', 'Drag to resize', 'system');
