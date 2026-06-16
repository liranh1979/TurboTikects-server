ALTER TABLE field_definitions
    ADD COLUMN field_visibility VARCHAR(30) NOT NULL DEFAULT 'all';

UPDATE field_definitions SET field_visibility = 'admin_only' WHERE is_admin_only = 1;
