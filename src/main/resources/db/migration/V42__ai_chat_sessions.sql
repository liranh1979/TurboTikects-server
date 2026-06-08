CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id      INT          NOT NULL,
    ticket_id    BIGINT       NULL,
    session_type VARCHAR(32)  NOT NULL DEFAULT 'user_help',
    status       VARCHAR(32)  NOT NULL DEFAULT 'active',
    title        VARCHAR(255) NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_user   FOREIGN KEY (user_id)   REFERENCES users(red_id),
    CONSTRAINT fk_chat_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL,
    INDEX idx_chat_user   (user_id, created_at),
    INDEX idx_chat_ticket (ticket_id)
);

CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    role       VARCHAR(16) NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id)
        REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    INDEX idx_msg_session_time (session_id, created_at)
);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
  ('en', 'eu_portal_welcome',       'How can we help you?',                              'system'),
  ('en', 'eu_portal_subtitle',      'Chat with our AI or create a support ticket.',      'system'),
  ('en', 'eu_chat_btn',             'Chat with AI',                                      'system'),
  ('en', 'eu_my_tickets_btn',       'My Tickets',                                        'system'),
  ('en', 'eu_new_ticket_btn',       'New Ticket',                                        'system'),
  ('en', 'chat_new_session',        'New Chat',                                          'system'),
  ('en', 'chat_send_placeholder',   'Type a message… (Enter to send)',                   'system'),
  ('en', 'chat_send_btn',           'Send',                                              'system'),
  ('en', 'chat_escalate_btn',       'Create a Ticket from this Chat',                    'system'),
  ('en', 'chat_escalate_hint',      'AI couldn''t fully resolve your issue? Let us open a ticket.', 'system'),
  ('en', 'chat_escalated_banner',   'Ticket created from this conversation',             'system'),
  ('en', 'chat_thinking',           'AI is thinking…',                                   'system'),
  ('en', 'ai_consult_title',        'AI Consultation',                                   'system'),
  ('en', 'ai_consult_btn',          'Consult AI',                                        'system'),
  ('en', 'ai_consult_saved',        'Conversation saved — resume anytime',               'system'),
  ('en', 'my_tickets_title',        'My Tickets',                                        'system'),
  ('en', 'no_my_tickets',           'You have no tickets yet.',                          'system');
