-- Draft-save support for the AI Action Item Wizard: an entry can now be saved mid-wizard, at any
-- step, and resumed later, instead of the wizard being all-or-nothing. 'complete' is the default
-- so every existing row (and every caller not yet updated to send status) keeps behaving exactly
-- as before. Only the Workflow Designer's "Add from Library" picker filters to status=complete —
-- a draft has no response mapping yet and would silently no-op if wired into a live template.
ALTER TABLE action_item_library
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'complete';
