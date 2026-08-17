-- New built-in "Solution" ticket field: a rich-text field for recording the actual fix,
-- separate from `description` (the original problem). Supports inline images/video/files
-- via the RichTextEditor's new upload button, each of which is also a real Attachment row
-- (entityType='ticket'), not just embedded HTML — see AttachmentController/AttachmentService.
-- Same shape as description's own seed row in V30__ticket_system_fields.sql.

ALTER TABLE tickets ADD COLUMN solution TEXT NULL;

INSERT IGNORE INTO field_definitions
    (entity_type, field_key, field_type, is_list_visible, is_detail_visible, display_order, is_system, field_options)
VALUES
    ('ticket', 'solution', 'text', 0, 1, 9, 1, NULL);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'solution', 'Solution', 'ticket_fields'),

    ('en', 'ticket_solution_copy_ai_btn',     'Copy from AI Solution', 'system'),
    ('en', 'ticket_solution_no_ai_solution',  'No AI Solution has been saved for this ticket yet.', 'system'),
    ('en', 'ticket_solution_overwrite_confirm', 'This will replace the current Solution content with the AI Solution text. Continue?', 'system'),
    ('en', 'ticket_solution_insert_file_btn', 'Insert File', 'system'),
    ('en', 'ticket_solution_uploading',       'Uploading…', 'system'),
    ('en', 'ticket_solution_upload_failed',   'Upload failed. Please try again.', 'system');
