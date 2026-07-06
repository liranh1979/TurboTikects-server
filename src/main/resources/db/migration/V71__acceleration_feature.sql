-- 1. Add acceleration column to tickets
ALTER TABLE tickets ADD COLUMN acceleration INT NULL DEFAULT 0;

-- 2. Add interval column to system_settings
ALTER TABLE system_settings ADD COLUMN acceleration_cron_interval INT NOT NULL DEFAULT 5;

-- 3. Acceleration rules table
CREATE TABLE acceleration_rules (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    description        TEXT,
    execution_order    INT          NOT NULL DEFAULT 0,
    trigger_conditions LONGTEXT     NOT NULL,
    actions            LONGTEXT     NOT NULL,
    generated_script   LONGTEXT,
    is_active          TINYINT(1)   NOT NULL DEFAULT 1,
    last_error         TEXT,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL
);

-- 4. Translation keys (English only, type='system')
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
-- Navigation / Settings card
('en', 'acceleration_nav_label',                'Ticket Acceleration',                                                          'system'),
('en', 'acceleration_card_label',               'Ticket Acceleration',                                                          'system'),
('en', 'acceleration_card_subtitle',            'Auto-escalation rules',                                                        'system'),
-- Page header
('en', 'acceleration_page_title',               'Ticket Acceleration',                                                          'system'),
('en', 'acceleration_page_subtitle',            'Define rules that automatically escalate tickets based on time and conditions.','system'),
-- List view
('en', 'acceleration_add_rule_btn',             'Add Rule',                                                                     'system'),
('en', 'acceleration_list_empty',               'No acceleration rules defined yet.',                                           'system'),
('en', 'acceleration_list_order_col',           'Order',                                                                        'system'),
('en', 'acceleration_list_name_col',            'Name',                                                                         'system'),
('en', 'acceleration_list_status_col',          'Status',                                                                       'system'),
('en', 'acceleration_list_error_col',           'Error',                                                                        'system'),
('en', 'acceleration_list_actions_col',         'Actions',                                                                      'system'),
('en', 'acceleration_run_now_btn',              'Run Now',                                                                      'system'),
('en', 'acceleration_run_now_confirm',          'Run this rule against all matching tickets now?',                              'system'),
-- Form
('en', 'acceleration_form_name_label',          'Rule Name',                                                                    'system'),
('en', 'acceleration_form_desc_label',          'Description',                                                                  'system'),
('en', 'acceleration_form_order_label',         'Execution Order',                                                              'system'),
('en', 'acceleration_form_active_label',        'Active',                                                                       'system'),
-- Condition builder
('en', 'acceleration_conditions_title',         'Trigger Conditions',                                                           'system'),
('en', 'acceleration_conditions_hint',          'ALL conditions must be true for a ticket to be processed.',                    'system'),
('en', 'acceleration_add_condition',            'Add Condition',                                                                'system'),
('en', 'acceleration_cond_status',              'Ticket Status',                                                                'system'),
('en', 'acceleration_cond_created_before_hours','Created More Than N Hours Ago',                                                'system'),
('en', 'acceleration_cond_acceleration_lt',     'Acceleration Level Less Than',                                                 'system'),
('en', 'acceleration_cond_has_no_assignee',     'Has No Assignee',                                                              'system'),
('en', 'acceleration_op_equals',                'Equals',                                                                       'system'),
('en', 'acceleration_op_not_equals',            'Not Equals',                                                                   'system'),
-- Action builder
('en', 'acceleration_actions_title',            'Actions',                                                                      'system'),
('en', 'acceleration_actions_hint',             'All actions run when conditions are met.',                                      'system'),
('en', 'acceleration_add_action',               'Add Action',                                                                   'system'),
('en', 'acceleration_action_set_acceleration',  'Set Acceleration To',                                                          'system'),
('en', 'acceleration_action_set_status',        'Set Status To',                                                                'system'),
('en', 'acceleration_action_assign_user',       'Assign to User ID',                                                            'system'),
('en', 'acceleration_action_assign_group',      'Assign to Group ID',                                                           'system'),
-- Groovy viewer
('en', 'acceleration_script_title',             'Generated Script',                                                             'system'),
('en', 'acceleration_script_hint',              'Read-only. Click Regenerate after changing conditions or actions.',             'system'),
('en', 'acceleration_regenerate_btn',           'Regenerate Script',                                                            'system'),
-- Dry run
('en', 'acceleration_dryrun_title',             'Dry Run',                                                                      'system'),
('en', 'acceleration_dryrun_hint',              'Test this rule against a real ticket without saving any changes.',              'system'),
('en', 'acceleration_dryrun_ticket_id_label',   'Ticket ID',                                                                    'system'),
('en', 'acceleration_dryrun_run_btn',           'Dry Run',                                                                      'system'),
('en', 'acceleration_dryrun_no_change',         'No changes would be made to this ticket.',                                     'system'),
('en', 'acceleration_dryrun_would_change',      'The following changes would be applied:',                                      'system'),
('en', 'acceleration_dryrun_field_col',         'Field',                                                                        'system'),
('en', 'acceleration_dryrun_from_col',          'Current Value',                                                                'system'),
('en', 'acceleration_dryrun_to_col',            'New Value',                                                                    'system'),
-- Error / status
('en', 'acceleration_last_error_label',         'Last Error',                                                                   'system'),
-- System settings
('en', 'acceleration_interval_label',           'Acceleration Check Interval (minutes)',                                        'system'),
('en', 'acceleration_interval_hint',            'How often to check and apply rules. Set to 0 to disable.',                    'system'),
-- Activity log
('en', 'acceleration_activity_applied',         'Acceleration rule applied',                                                    'system'),
('en', 'acceleration_system_actor',             'System',                                                                       'system'),
-- Field label in activity log change cards
('en', 'acceleration_field_label',              'Acceleration',                                                                 'system');
