-- Companies table
CREATE TABLE companies (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Add company FK to users, user_groups, tickets
ALTER TABLE users
    ADD COLUMN company_id INT NULL,
    ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL;

ALTER TABLE user_groups
    ADD COLUMN company_id INT NULL,
    ADD CONSTRAINT fk_groups_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL;

ALTER TABLE tickets
    ADD COLUMN company_id INT NULL,
    ADD CONSTRAINT fk_tickets_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
    ADD INDEX idx_tickets_company (company_id);

-- Translation keys
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
('en', 'companies_settings_card',   'Companies',                                              'system'),
('en', 'companies_settings_desc',   'Manage client companies and their data isolation',        'system'),
('en', 'companies_title',           'Companies',                                              'system'),
('en', 'companies_empty',           'No companies have been added yet.',                       'system'),
('en', 'add_company',               'New Company',                                            'system'),
('en', 'edit_company',              'Edit Company',                                           'system'),
('en', 'delete_company',            'Delete Company',                                         'system'),
('en', 'company_name_label',        'Company Name',                                           'system'),
('en', 'company_desc_label',        'Description',                                            'system'),
('en', 'company_timezone_label',    'Timezone',                                               'system'),
('en', 'company_users_count',       '{{count}} users',                                        'system'),
('en', 'company_groups_count',      '{{count}} groups',                                       'system'),
('en', 'companies_none_assigned',   '— No Company —',                                         'system'),
('en', 'company_col_name',          'Name',                                                   'system'),
('en', 'company_col_users',         'Users',                                                  'system'),
('en', 'company_col_groups',        'Groups',                                                 'system'),
('en', 'company_col_timezone',      'Timezone',                                               'system'),
('en', 'company_global_hours_btn',  'Global Hours',                                           'system'),
('en', 'company_save_btn',          'Save Company',                                           'system'),
('en', 'company_section',           'Company',                                                'system');
