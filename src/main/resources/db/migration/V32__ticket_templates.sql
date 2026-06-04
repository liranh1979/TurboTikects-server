CREATE TABLE IF NOT EXISTS ticket_templates (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_template_versions (
    id             BIGINT     AUTO_INCREMENT PRIMARY KEY,
    template_id    BIGINT     NOT NULL,
    version_number INT        NOT NULL DEFAULT 1,
    layout         JSON       NOT NULL,
    is_current     TINYINT(1) NOT NULL DEFAULT 1,
    created_at     TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ttv_template FOREIGN KEY (template_id) REFERENCES ticket_templates(id) ON DELETE CASCADE,
    UNIQUE KEY uq_template_version (template_id, version_number)
);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
  ('en', 'tickets_and_templates',          'Tickets & Templates',                                                          'system'),
  ('en', 'template_management',            'Template Management',                                                          'system'),
  ('en', 'create_template',                'Create Template',                                                              'system'),
  ('en', 'template_name_label',            'Template Name',                                                               'system'),
  ('en', 'template_description_label',     'Description',                                                                  'system'),
  ('en', 'template_edit_mode',             'Edit',                                                                         'system'),
  ('en', 'template_preview_mode',          'Preview',                                                                      'system'),
  ('en', 'template_save',                  'Save Template',                                                                'system'),
  ('en', 'template_saved',                 'Template saved',                                                               'system'),
  ('en', 'template_version_label',         'Version',                                                                      'system'),
  ('en', 'template_mandatory_badge',       'Required',                                                                     'system'),
  ('en', 'template_add_field',             'Add Field',                                                                    'system'),
  ('en', 'template_default_value_label',   'Default Value',                                                               'system'),
  ('en', 'template_ai_generate',           'Generate with AI',                                                             'system'),
  ('en', 'template_ai_prompt_placeholder', 'Describe the ticket type (e.g. "IT support request with priority and SLA")',  'system'),
  ('en', 'template_ai_generating',         'Generating...',                                                                'system'),
  ('en', 'template_ai_apply',              'Apply Suggestion',                                                             'system'),
  ('en', 'no_templates',                   'No templates yet. Create your first template.',                                'system'),
  ('en', 'delete_template',                'Delete Template',                                                              'system'),
  ('en', 'template_available_fields',      'Available Fields',                                                             'system'),
  ('en', 'template_layout_fields',         'Template Fields',                                                              'system');
