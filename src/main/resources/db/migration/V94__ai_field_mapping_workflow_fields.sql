-- External API / MCP field-mapping pickers (used by both the AI Workflow Builder and the Workflow
-- Designer) now offer Workflow Fields (from Workflow Fields Manager) as a source/target alongside
-- ticket fields, for both request-side input and response-side output.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'eae_ticket_fields_optgroup', 'Ticket Fields', 'system'),
    ('en', 'eae_workflow_fields_optgroup', 'Workflow Fields', 'system'),
    ('en', 'eae_workflow_fields_none', '(none defined yet)', 'system');
