-- FEAT-05.v2: Custom Scheduled Reports — admin-authored report definitions with an AI
-- query-builder agent, optional scheduling (email an admin or a group), CSV/PDF export,
-- and an AI summary + confidence-gated improvement tips on top of the results.
-- See V2/repoets/feat-05-01-data-model.html for the full spec/ERD.

CREATE TABLE IF NOT EXISTS report_definitions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       TEXT NULL,
    entity_type       VARCHAR(32)  NOT NULL DEFAULT 'ticket',
    query_spec        JSON NOT NULL,
    export_formats    JSON NOT NULL,
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    ai_generated      TINYINT(1)   NOT NULL DEFAULT 0,
    last_ai_prompt    TEXT NULL,
    created_by        INT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 1:1 with report_definitions — a report has zero or one schedule. UNIQUE enforces this
-- at the DB level so the app never has to reconcile more than one schedule per report.
CREATE TABLE IF NOT EXISTS report_schedules (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_definition_id  BIGINT NOT NULL UNIQUE,
    cron_expression       VARCHAR(64) NOT NULL,
    frequency_type        VARCHAR(16) NOT NULL,
    recipient_user_ids    JSON NULL,
    recipient_group_id    INT NULL,
    last_run_at           TIMESTAMP NULL,
    next_run_at           TIMESTAMP NOT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_schedule_definition FOREIGN KEY (report_definition_id)
        REFERENCES report_definitions (id) ON DELETE CASCADE
);

CREATE INDEX idx_report_schedules_due ON report_schedules (next_run_at);

-- Execution history — one row per manual Test click or scheduled fire. Backs the list's
-- "Last Run" column and is where generated files + the AI summary/tips attach.
CREATE TABLE IF NOT EXISTS report_runs (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_definition_id  BIGINT NOT NULL,
    triggered_by          VARCHAR(16) NOT NULL,
    row_count             INT NULL,
    status                VARCHAR(16) NOT NULL,
    ai_summary            TEXT NULL,
    ai_tips               JSON NULL,
    csv_path              VARCHAR(512) NULL,
    pdf_path              VARCHAR(512) NULL,
    started_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMP NULL,
    error_message         TEXT NULL,
    CONSTRAINT fk_report_run_definition FOREIGN KEY (report_definition_id)
        REFERENCES report_definitions (id) ON DELETE CASCADE
);

CREATE INDEX idx_report_runs_definition ON report_runs (report_definition_id, started_at);

INSERT IGNORE INTO permissions (permission_key, display_order) VALUES ('MANAGE_REPORTS', 12);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'permission_manage_reports_name', 'Reports', 'system'),
    ('en', 'permission_manage_reports_desc', 'Can build, schedule, and manage custom reports', 'system'),
    ('en', 'reports_tab',                'Reports', 'system'),
    ('en', 'reports_page_title',         'Reports', 'system'),
    ('en', 'reports_add_btn',            '+ New Report', 'system'),
    ('en', 'reports_list_empty',         'No reports configured yet.', 'system'),
    ('en', 'reports_col_name',           'Name', 'system'),
    ('en', 'reports_col_enabled',        'Enabled', 'system'),
    ('en', 'reports_col_schedule',       'Schedule', 'system'),
    ('en', 'reports_col_last_run',       'Last Run', 'system'),
    ('en', 'reports_col_actions',        'Actions', 'system'),
    ('en', 'reports_test_btn',           'Test', 'system'),
    ('en', 'reports_edit_btn',           'Edit', 'system'),
    ('en', 'reports_delete_btn',         'Delete', 'system'),
    ('en', 'reports_delete_confirm',     'Delete this report? This cannot be undone.', 'system'),
    ('en', 'reports_schedule_manual_only', 'Manual only', 'system'),
    ('en', 'reports_never_run',          'Never run', 'system'),
    ('en', 'reports_form_title_new',     'New Report', 'system'),
    ('en', 'reports_form_title_edit',    'Edit Report', 'system'),
    ('en', 'reports_form_name_label',    'Report name', 'system'),
    ('en', 'reports_form_description_label', 'Description', 'system'),
    ('en', 'reports_ai_describe_label',  'Describe the report you want', 'system'),
    ('en', 'reports_ai_build_btn',       '✨ Ask AI to Build This Query', 'system'),
    ('en', 'reports_ai_proposal_title',  'AI Proposal — review before saving', 'system'),
    ('en', 'reports_selected_fields_label', 'Selected fields', 'system'),
    ('en', 'reports_conditions_label',   'Conditions', 'system'),
    ('en', 'reports_add_condition_btn',  '+ Add condition', 'system'),
    ('en', 'reports_add_or_group_btn',   '+ Add OR group', 'system'),
    ('en', 'reports_preview_label',      'Preview', 'system'),
    ('en', 'reports_preview_capped_note','capped at 50 for preview', 'system'),
    ('en', 'reports_export_formats_label', 'Export Formats', 'system'),
    ('en', 'reports_schedule_section_label', 'Schedule', 'system'),
    ('en', 'reports_schedule_enable_label', 'Send this report on a schedule', 'system'),
    ('en', 'reports_frequency_label',    'Frequency', 'system'),
    ('en', 'reports_freq_daily',         'Daily', 'system'),
    ('en', 'reports_freq_weekly',        'Weekly', 'system'),
    ('en', 'reports_freq_monthly',       'Monthly', 'system'),
    ('en', 'reports_freq_custom',        'Custom cron', 'system'),
    ('en', 'reports_next_run_label',     'Next run', 'system'),
    ('en', 'reports_recipients_label',   'Recipients', 'system'),
    ('en', 'reports_recipients_users',   'Admin user(s)', 'system'),
    ('en', 'reports_recipients_group',   'Group', 'system'),
    ('en', 'reports_save_btn',           'Save Report', 'system'),
    ('en', 'reports_ai_summary_label',   'AI Summary', 'system'),
    ('en', 'reports_ai_tips_label',      'Tips for Improvement', 'system'),
    ('en', 'reports_no_data_title',      'No data was found', 'system'),
    ('en', 'reports_no_data_message',    'No data was found matching this report''s criteria for the selected period.', 'system');
