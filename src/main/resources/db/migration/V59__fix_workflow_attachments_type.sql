-- Fix workflow attachments field type from generic 'text' to proper 'file'
UPDATE field_definitions
SET field_type = 'file'
WHERE entity_type = 'workflow' AND field_key = 'attachments';
