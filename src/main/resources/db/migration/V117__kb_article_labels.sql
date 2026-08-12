-- KB article tags now reuse the exact same tag/label catalog tickets use (ticket_labels),
-- via a proper join table mirroring ticket_label_assignments, instead of a free-text JSON
-- array. When a KB article is generated from a resolved ticket, its labels are inherited from
-- that ticket's real label assignments rather than invented by the AI.

CREATE TABLE kb_article_labels (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_article_id  BIGINT NOT NULL,
    label_id       BIGINT NOT NULL,
    UNIQUE KEY uq_kb_article_label (kb_article_id, label_id),
    CONSTRAINT fk_kb_article_label_article FOREIGN KEY (kb_article_id) REFERENCES kb_articles(id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_article_label_label FOREIGN KEY (label_id) REFERENCES ticket_labels(id) ON DELETE CASCADE
);

-- Migrate the one built-in article's existing free-text tags (V116) into real ticket_labels,
-- so no data is silently dropped when the old `tags` column goes away below.
INSERT IGNORE INTO ticket_labels (label_key, color) VALUES
    ('getting-started', '#3b82f6'),
    ('navigation',       '#8b5cf6'),
    ('main-menu',        '#06b6d4');

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'getting-started', 'Getting Started', 'label'),
    ('en', 'navigation',      'Navigation',      'label'),
    ('en', 'main-menu',       'Main Menu',       'label');

INSERT IGNORE INTO kb_article_labels (kb_article_id, label_id)
SELECT a.id, l.id
FROM kb_articles a
JOIN ticket_labels l ON l.label_key IN ('getting-started', 'navigation', 'main-menu')
WHERE a.title = 'How to Use TurboTikects — Main Menus Overview';

ALTER TABLE kb_articles DROP COLUMN tags;

-- Tags are now picked via the same LabelPickerControl chip UI tickets use, not typed as
-- comma-separated text — refresh the now-stale label copy from V115.
UPDATE dynamic_translations SET translated_text = 'Tags'
WHERE lang_code = 'en' AND translation_key = 'kb_form_tags_label' AND type = 'system';

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'kb_col_tags', 'Tags', 'system');
