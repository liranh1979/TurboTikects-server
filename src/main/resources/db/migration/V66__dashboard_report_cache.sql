CREATE TABLE dashboard_report_cache (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    INT          NULL,
    dashboard_id  VARCHAR(64)  NOT NULL,
    fingerprint   VARCHAR(128) NOT NULL,
    report_json   JSON         NOT NULL,
    generated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_dashboard_company (dashboard_id, company_id)
);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'dashboard_nav_label',            'Dashboard',                                                    'system'),
('en', 'dashboard_tickets_title',        'Tickets Dashboard',                                            'system'),
('en', 'dashboard_ai_report_title',      'AI Insights',                                                  'system'),
('en', 'dashboard_ai_report_summary',    'Summary',                                                      'system'),
('en', 'dashboard_recurring_problems',   'Recurring Problems',                                           'system'),
('en', 'dashboard_confidence_label',     'confidence',                                                   'system'),
('en', 'dashboard_no_fix_flag',          'Not enough certainty to suggest a fix — flagged for review.', 'system'),
('en', 'dashboard_suggested_fix',        'Suggested fix',                                                'system'),
('en', 'dashboard_tab_day',              'Day',                                                          'system'),
('en', 'dashboard_tab_week',             'Week',                                                         'system'),
('en', 'dashboard_tab_month',            'Month',                                                        'system'),
('en', 'dashboard_tickets_chart_title',  'Tickets Created',                                              'system'),
('en', 'dashboard_actions_chart_title',  'Action Items Created',                                         'system'),
('en', 'dashboard_report_loading',       'Analyzing your tickets…',                                     'system'),
('en', 'dashboard_report_error',         'Could not generate the AI report. Try again later.',          'system'),
('en', 'dashboard_report_cached_label',  'Cached',                                                       'system'),
('en', 'dashboard_report_fresh_label',   'Freshly generated',                                            'system'),
('en', 'dashboard_no_data',              'Not enough ticket activity yet to show this chart.',           'system'),
('en', 'dashboard_retry_btn',            'Retry',                                                        'system');
