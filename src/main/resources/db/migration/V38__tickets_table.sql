CREATE TABLE IF NOT EXISTS tickets (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title                VARCHAR(512) NOT NULL,
    description          TEXT,
    status               VARCHAR(32)  NOT NULL DEFAULT 'new',
    template_id          BIGINT       NOT NULL,
    template_version_id  BIGINT       NOT NULL,
    request_user_id      INT          NOT NULL,
    responsible_user_id  INT          NULL,
    responsible_group_id INT          NULL,
    ticket_data          JSON         NULL,
    version              INT          NOT NULL DEFAULT 1,
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_template         FOREIGN KEY (template_id)         REFERENCES ticket_templates(id),
    CONSTRAINT fk_ticket_template_version FOREIGN KEY (template_version_id) REFERENCES ticket_template_versions(id),
    CONSTRAINT fk_ticket_request_user     FOREIGN KEY (request_user_id)     REFERENCES users(red_id),
    CONSTRAINT fk_ticket_resp_user        FOREIGN KEY (responsible_user_id) REFERENCES users(red_id),
    CONSTRAINT fk_ticket_resp_group       FOREIGN KEY (responsible_group_id) REFERENCES user_groups(ref_id),
    FULLTEXT INDEX ft_ticket_search (title, description)
);

CREATE TABLE IF NOT EXISTS ticket_label_assignments (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id  BIGINT NOT NULL,
    label_id   BIGINT NOT NULL,
    UNIQUE KEY uq_ticket_label (ticket_id, label_id),
    CONSTRAINT fk_tla_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_tla_label  FOREIGN KEY (label_id)  REFERENCES ticket_labels(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ticket_update_queue (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL,
    operation   VARCHAR(32)  NOT NULL,
    payload     JSON         NOT NULL,
    actor_id    INT          NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    claimed_by  VARCHAR(128) NULL,
    claimed_at  TIMESTAMP    NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_queue_status (status, id)
);

CREATE TABLE IF NOT EXISTS ticket_activity_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL,
    actor_id    INT         NOT NULL,
    operation   VARCHAR(32) NOT NULL,
    changes     JSON        NOT NULL,
    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_ticket (ticket_id, created_at)
);

CREATE TABLE IF NOT EXISTS ticket_presence (
    ticket_id  BIGINT    NOT NULL,
    user_id    INT       NOT NULL,
    last_seen  TIMESTAMP NOT NULL,
    PRIMARY KEY (ticket_id, user_id)
);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
  ('en', 'all_tickets',                   'All Tickets',                      'system'),
  ('en', 'my_tickets',                    'Assigned to me',                   'system'),
  ('en', 'new_ticket',                    'New Ticket',                       'system'),
  ('en', 'create_ticket',                 'Create a ticket',                  'system'),
  ('en', 'ticket_id_col',                 'ID',                               'system'),
  ('en', 'ticket_title_col',              'Title',                            'system'),
  ('en', 'ticket_status_col',             'Status',                           'system'),
  ('en', 'ticket_labels_col',             'Labels',                           'system'),
  ('en', 'ticket_assignee_col',           'Assignee',                         'system'),
  ('en', 'ticket_template_col',           'Template',                         'system'),
  ('en', 'ticket_updated_col',            'Updated',                          'system'),
  ('en', 'ticket_search_placeholder',     'Search title & description…',      'system'),
  ('en', 'ticket_filter_labels',          'Labels',                           'system'),
  ('en', 'ticket_filter_status',          'Status',                           'system'),
  ('en', 'ticket_filter_responsible',     'Assignee',                         'system'),
  ('en', 'ticket_filter_template',        'Template',                         'system'),
  ('en', 'bulk_delete',                   'Delete',                           'system'),
  ('en', 'bulk_update_status',            'Update Status',                    'system'),
  ('en', 'bulk_update_responsible',       'Update Responsible',               'system'),
  ('en', 'bulk_add_label',                'Add Label',                        'system'),
  ('en', 'bulk_remove_label',             'Remove Label',                     'system'),
  ('en', 'selected_count',                'selected',                         'system'),
  ('en', 'no_tickets',                    'No tickets yet.',                  'system'),
  ('en', 'ticket_mode_manual',            'Manual',                           'system'),
  ('en', 'ticket_mode_ai',                'AI Agent',                         'system'),
  ('en', 'ai_analyze_btn',                'Analyze & Match Template',         'system'),
  ('en', 'ai_analyzing',                  'Analyzing…',                       'system'),
  ('en', 'ai_matched_template',           'Matched template',                 'system'),
  ('en', 'ai_suggested_priority',         'Suggested priority',               'system'),
  ('en', 'ai_suggested_labels',           'Suggested labels',                 'system'),
  ('en', 'ai_prefilled_banner',           'AI pre-filled fields from your description. Review before submitting.', 'system'),
  ('en', 'ai_accept_all',                 'Accept all',                       'system'),
  ('en', 'ai_clear_values',               'Clear AI values',                  'system'),
  ('en', 'ticket_template_search',        'Search templates…',                'system'),
  ('en', 'save_draft',                    'Save draft',                       'system'),
  ('en', 'create_ticket_btn',             'Create ticket',                    'system'),
  ('en', 'cancel_btn',                    'Cancel',                           'system'),
  ('en', 'loading_tickets',               'Loading tickets…',                 'system'),
  ('en', 'ticket_conflict_banner',        'This ticket was updated while you were editing. Your draft is preserved.', 'system'),
  ('en', 'ticket_synced_toast',           'Synced with latest changes',       'system'),
  ('en', 'ticket_editing_indicator',      'is editing this ticket',           'system'),
  ('en', 'ai_processing_indicator',       'AI is updating this ticket…',      'system'),
  ('en', 'refresh_and_merge',             'Refresh & merge',                  'system'),
  ('en', 'keep_my_draft',                 'Keep my draft',                    'system'),
  ('en', 'ticket_activity_log',           'Activity Log',                     'system'),
  ('en', 'ticket_updated_by',             'Updated by',                       'system');
