-- Activity log: add type + metadata columns
ALTER TABLE ticket_activity_log
  ADD COLUMN activity_type VARCHAR(32) NOT NULL DEFAULT 'manual' AFTER operation,
  ADD COLUMN metadata JSON AFTER changes;

-- Translations: permission
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'permission_manage_email_name', 'Manage Email',                                   'system'),
('en', 'permission_manage_email_desc', 'Configure email mailboxes and processing rules', 'system');

-- Translations: settings card
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'settings_email_manager',   'Email Integration',                             'system'),
('en', 'email_manager_desc',       'Connect mailboxes, auto-create tickets from email', 'system');

-- Translations: mailbox form
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'add_mailbox',               'Add Mailbox',                    'system'),
('en', 'mailbox_display_name',      'Display Name',                   'system'),
('en', 'mailbox_email_address',     'Email Address',                  'system'),
('en', 'mailbox_protocol',          'Protocol',                       'system'),
('en', 'protocol_imap_smtp',        'IMAP / SMTP',                    'system'),
('en', 'protocol_oauth2_gmail',     'Gmail (OAuth2)',                  'system'),
('en', 'protocol_oauth2_microsoft', 'Microsoft 365 (OAuth2)',          'system'),
('en', 'imap_settings',             'IMAP Settings',                  'system'),
('en', 'smtp_settings',             'SMTP Settings',                  'system'),
('en', 'oauth2_settings',           'OAuth2 Settings',                'system'),
('en', 'oauth2_client_id',          'Client ID',                      'system'),
('en', 'oauth2_client_secret',      'Client Secret',                  'system'),
('en', 'oauth2_tenant_id',          'Tenant ID (Microsoft)',           'system'),
('en', 'authorize_btn',             'Authorize',                      'system'),
('en', 'authorized_label',          'Authorized',                     'system'),
('en', 'can_receive_label',         'Receive emails (create tickets)', 'system'),
('en', 'can_send_label',            'Send emails (replies)',           'system'),
('en', 'is_default_sender_label',   'Default sender mailbox',         'system'),
('en', 'poll_interval_label',       'Check every (seconds)',           'system'),
('en', 'ai_enabled_label',          'Enable AI triage',               'system'),
('en', 'ai_confidence_label',       'Min AI confidence (%)',          'system'),
('en', 'unknown_sender_create',     'Create user from unknown sender', 'system'),
('en', 'unknown_sender_ignore',     'Ignore emails from unknown senders', 'system'),
('en', 'ignore_signature_label',    'Strip email signatures',         'system'),
('en', 'email_filters_title',       'Allowed / Blocked Senders',      'system'),
('en', 'filter_whitelist_label',    'Whitelist (allow only)',          'system'),
('en', 'filter_blacklist_label',    'Blacklist (block)',               'system'),
('en', 'filter_pattern_hint',       'e.g. user@domain.com or *@spam.com', 'system'),
('en', 'test_connection_btn',       'Test Connection',                 'system'),
('en', 'mailbox_connected',         'Connected',                      'system'),
('en', 'mailbox_connection_failed', 'Connection failed',              'system'),
('en', 'last_checked_label',        'Last checked',                   'system'),
('en', 'no_mailboxes_yet',          'No mailboxes configured yet.',   'system'),
('en', 'mailbox_status_active',     'Active',                         'system'),
('en', 'mailbox_status_inactive',   'Inactive',                       'system'),
('en', 'edit_mailbox',              'Edit Mailbox',                   'system'),
('en', 'delete_mailbox',            'Delete Mailbox',                 'system'),
('en', 'set_default_sender',        'Set as Default',                 'system');

-- Translations: activity log types
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'activity_type_manual',         'Note',           'system'),
('en', 'activity_type_ai',             'AI',             'system'),
('en', 'activity_type_ai_solution',    'AI Solution',    'system'),
('en', 'activity_type_email_inbound',  'Email In',       'system'),
('en', 'activity_type_email_outbound', 'Email Out',      'system'),
('en', 'activity_type_field_change',   'Changed',        'system'),
('en', 'activity_type_status_change',  'Status',         'system'),
('en', 'activity_type_system',         'System',         'system'),
('en', 'activity_expand_btn',          'View Full Activity', 'system'),
('en', 'activity_filter_label',        'Filter by type', 'system'),
('en', 'activity_search_placeholder',  'Search activity...', 'system'),
('en', 'activity_load_more',           'Load more',      'system');
