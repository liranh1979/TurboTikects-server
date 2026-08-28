-- Real bug found live (FEAT-19): a "mpc flight" workflow item had 5 response captures
-- (departure_airport_name/city/country/image/thumbnail) all mapped to the SAME target field
-- this.departure_airport_details — applyTarget/applyNodelistTarget overwrite on every non-nodelist
-- target, so only the last-applied mapping's value ever actually lands, with no error anywhere.
-- ExternalApiFieldMappingsEditor and McpResponseMappingsEditor now warn inline when 2+ captures
-- share a non-nodelist target.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'response_mapping_collision_hint', 'Multiple captures are mapped to the same field — only the last one applied will actually be saved: {{targets}}', 'system');
