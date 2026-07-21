-- external_api/mcp_tool workflow action items now write a TicketActivityLogEntity
-- ("WORKFLOW_ACTION_APPLIED", actorId=0/system) when they update a ticket field, matching the
-- existing ACCELERATION_APPLIED precedent — previously these updates were completely invisible in
-- the ticket's Activity Log.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'activity_workflow_action_applied', '{{itemType}} action applied', 'system');
