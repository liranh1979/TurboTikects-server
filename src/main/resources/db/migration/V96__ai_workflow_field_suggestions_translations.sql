-- AI Workflow Builder: AI-drafted external_api/mcp_tool action items can now suggest creating a
-- new Workflow Field (entity_type='workflow') when no existing one is a good name+type match for
-- a "this.<key>" reference, mirroring LDAP import's missing-field-suggestion pattern but resolved
-- inline in this wizard's Step 2 review instead of a page-navigation detour.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_missing_fields_heading', 'Suggested new Workflow Fields', 'system'),
    ('en', 'awb_missing_fields_hint', 'No existing workflow field was a good name/type match for these — create them so the mappings below resolve to real fields.', 'system'),
    ('en', 'awb_missing_field_used_by', 'used by: {{locations}}', 'system'),
    ('en', 'awb_missing_field_create_failed', 'Could not create this field — it may already exist. Try again.', 'system'),
    ('en', 'awb_missing_field_create_btn', 'Create field', 'system'),
    ('en', 'eae_pending_field_option', 'this.{{key}} (pending — create it below)', 'system');
