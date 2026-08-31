-- MCP tool call argument mapping gained a third source alongside "from ticket field"/"from earlier
-- capture": a fixed, admin-typed constant — for a tool parameter that's always the same value for
-- every ticket (e.g. a hotel-search tool's "rooms" argument), where mapping from a ticket field
-- would mean inventing a fake constant field just to hold it. See McpActionExecutor.coerceLiteral.

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'mcp_arg_fixed_value_option',      'fixed value',                    'system'),
('en', 'mcp_arg_fixed_value_placeholder', 'e.g. 1, true, or some text',     'system');
