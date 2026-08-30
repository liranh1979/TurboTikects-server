-- "Retry" button on a done/blocked external_api or mcp_tool workflow item's inspector panel
-- (WorkflowTreePanel.tsx) — lets an admin re-run the item after fixing a bad JSONPath, argument
-- mapping, or ticket value, without re-seeding the whole ticket. See WorkflowService.retryItem /
-- POST /workflow/items/{id}/retry.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'workflow_retry_action', 'Retry',                        'system'),
('en', 'workflow_retrying',     'Retrying…',                    'system'),
('en', 'workflow_retry_failed', 'Failed to retry this action',  'system');
