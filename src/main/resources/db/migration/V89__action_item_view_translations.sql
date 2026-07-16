-- New scoped "Action Item" view (ActionItemPage.tsx) lets a user assigned to one workflow item,
-- but not the ticket's requester or a TICKET_MANAGER, act on it without loading the full ticket
-- (which they're correctly forbidden from seeing — see WorkflowService.assertCanViewItem). Also
-- adds a clean error message for TicketEditPage's own load failure, previously uncaught.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'action_item_not_found', 'This item could not be found.', 'system'),
    ('en', 'action_item_no_access', 'You don''t have access to this item.', 'system'),
    ('en', 'action_item_no_pending_decision', 'There is nothing pending for you to decide on this item right now.', 'system'),
    ('en', 'ticket_no_access', 'You don''t have access to this ticket.', 'system'),
    ('en', 'ticket_not_found', 'This ticket could not be found.', 'system');
