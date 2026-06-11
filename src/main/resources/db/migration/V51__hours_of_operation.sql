-- Hours of Operation table (company_id NULL = global schedule)
CREATE TABLE hours_of_operation (
    id              INT         AUTO_INCREMENT PRIMARY KEY,
    company_id      INT         NULL,
    day_of_week     TINYINT     NOT NULL,   -- 0=Sun, 1=Mon, ... 6=Sat
    is_open         TINYINT(1)  NOT NULL DEFAULT 1,
    slot1_start     TIME        NULL,       -- morning start  (NULL = start of day)
    slot1_end       TIME        NULL,       -- morning end    (NULL = end of day, i.e. full day / 24h)
    slot2_start     TIME        NULL,       -- afternoon start (NULL = no break)
    slot2_end       TIME        NULL,       -- afternoon end
    UNIQUE KEY uq_hoo (company_id, day_of_week),
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Seed global 24/7 defaults (7 rows, all times NULL = open all day)
INSERT INTO hours_of_operation (company_id, day_of_week, is_open)
VALUES (NULL,0,1),(NULL,1,1),(NULL,2,1),(NULL,3,1),(NULL,4,1),(NULL,5,1),(NULL,6,1);

-- Translation keys
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'hoo_title',           'Hours of Operation',                                           'system'),
('en', 'hoo_global_title',    'Global Hours of Operation',                                    'system'),
('en', 'hoo_global_desc',     'Default schedule applied when a company has no custom hours.',  'system'),
('en', 'hoo_open',            'Open',                                                         'system'),
('en', 'hoo_closed',          'Closed',                                                       'system'),
('en', 'hoo_add_break',       '+ Add Lunch Break',                                            'system'),
('en', 'hoo_remove_break',    '× Remove Break',                                               'system'),
('en', 'hoo_slot1_start',     'Open',                                                         'system'),
('en', 'hoo_slot1_end',       'Break start',                                                  'system'),
('en', 'hoo_slot2_start',     'Break end',                                                    'system'),
('en', 'hoo_slot2_end',       'Close',                                                        'system'),
('en', 'hoo_ai_label',        'Describe your working hours in plain English…',                 'system'),
('en', 'hoo_ai_btn',          'Parse with AI',                                                'system'),
('en', 'hoo_ai_parsing',      'Parsing…',                                                     'system'),
('en', 'hoo_ai_hint',         'Example: "Monday to Friday 9am to 5pm, lunch 1pm–2pm, closed weekends"', 'system'),
('en', 'hoo_save_btn',        'Save Hours',                                                   'system'),
('en', 'hoo_save_success',    'Hours saved.',                                                 'system'),
('en', 'hoo_day_0',           'Sunday',                                                       'system'),
('en', 'hoo_day_1',           'Monday',                                                       'system'),
('en', 'hoo_day_2',           'Tuesday',                                                      'system'),
('en', 'hoo_day_3',           'Wednesday',                                                    'system'),
('en', 'hoo_day_4',           'Thursday',                                                     'system'),
('en', 'hoo_day_5',           'Friday',                                                       'system'),
('en', 'hoo_day_6',           'Saturday',                                                     'system');
