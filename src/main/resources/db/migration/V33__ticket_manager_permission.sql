-- Ticket Manager permission
INSERT IGNORE INTO permissions (permission_key, display_order) VALUES ('TICKET_MANAGER', 8);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
  ('en', 'permission_ticket_manager_name', 'Ticket Manager',                                      'system'),
  ('en', 'permission_ticket_manager_desc', 'Can manage tickets and be assigned as responsible',   'system');
