-- Backs the new super-admin-only "Dashboard Manager" setting (reorder Tickets Dashboard sections).
-- Null/empty means "use the built-in default order" — resolved in SystemSettingsService, no seed needed.
ALTER TABLE system_settings ADD COLUMN dashboard_section_order JSON NULL;
