-- New UI copy for the ticket detail page's "Create KB Article" button, which jumps straight into
-- the existing generate-from-ticket AI draft flow without the manual ticket-ID modal.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'kb_create_from_ticket_btn', 'Create KB Article', 'system'),
    ('en', 'kb_generate_from_ticket_loading', 'Drafting an article from this ticket…', 'system');
