-- Real bug found live: an MCP tool's argument-mapping row with no real mapping yet (either the AI
-- silently dropped it, or "Refresh Arguments" added a newly-discovered one) used to default to
-- "ticket.title" just so the <select> never looked empty — which made a genuinely unmapped
-- argument look like a deliberate (if wrong) choice instead of an obvious gap. FieldRefSelect now
-- supports a real, selectable "not mapped" option (see McpToolCallsEditor.tsx's ArgMappingRow).
-- Backend-side: TemplateService.aiSuggestMcpAction now deterministically guarantees every property
-- in the tool's REAL inputSchema.properties gets an argumentMappings entry (synthesizing a
-- "this.<key>" + matching missingWorkflowFields suggestion for anything the AI left out) — which
-- arguments exist isn't an AI decision, it's a fact of the tool's schema.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'field_ref_not_mapped_option', '— not mapped —', 'system');
