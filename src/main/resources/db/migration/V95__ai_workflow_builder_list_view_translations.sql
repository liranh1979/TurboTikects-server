-- AI Workflow Builder tab now opens on a management list of existing AI-built action items
-- (edit/delete in place) instead of always starting the generate wizard.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'awb_list_hint', 'AI-built action items (external API calls or MCP tool calls). Add one to any template from the Workflow Designer.', 'system'),
    ('en', 'awb_new_ai_item_btn', 'New AI Action Item', 'system'),
    ('en', 'awb_ai_items_list_empty', 'No AI action items yet — click "New AI Action Item" to build one.', 'system'),
    ('en', 'awb_back_to_list_btn', 'Back to List', 'system');
