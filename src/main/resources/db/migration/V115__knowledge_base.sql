-- FEAT-03: Knowledge Base — structured self-service article library, with AI-assisted authoring
-- (draft-from-resolved-ticket + AI review/expand/fix-mistakes, both always admin-reviewed before
-- save, never auto-applied). See V2/feature-03-kb.html for the spec.

CREATE TABLE kb_categories (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    icon          VARCHAR(8) NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT IGNORE INTO kb_categories (name, icon, display_order) VALUES
    ('Account & Access', '🔑', 1),
    ('Network',          '🌐', 2),
    ('Hardware',         '🖥️', 3),
    ('Software',         '💾', 4),
    ('Security',         '🛡️', 5);

CREATE TABLE kb_articles (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    body               LONGTEXT NOT NULL,
    category_id        BIGINT NULL,
    tags               JSON NULL,
    visibility         VARCHAR(16) NOT NULL DEFAULT 'internal',
    view_count         INT NOT NULL DEFAULT 0,
    helpful_count      INT NOT NULL DEFAULT 0,
    not_helpful_count  INT NOT NULL DEFAULT 0,
    author_id          INT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_kb_article_category FOREIGN KEY (category_id) REFERENCES kb_categories(id) ON DELETE SET NULL,
    FULLTEXT INDEX ft_kb_article_search (title, body)
);

CREATE TABLE ticket_kb_links (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT NOT NULL,
    article_id  BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ticket_kb_link (ticket_id, article_id),
    CONSTRAINT fk_kb_link_ticket  FOREIGN KEY (ticket_id)  REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_link_article FOREIGN KEY (article_id) REFERENCES kb_articles(id) ON DELETE CASCADE
);

INSERT IGNORE INTO permissions (permission_key, display_order) VALUES ('MANAGE_KNOWLEDGE_BASE', 12);

INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    ('en', 'permission_manage_knowledge_base_name', 'Knowledge Base', 'system'),
    ('en', 'permission_manage_knowledge_base_desc', 'Can create, edit, and manage Knowledge Base articles', 'system'),
    ('en', 'kb_nav_item', 'Knowledge Base', 'system'),
    ('en', 'kb_card_label', 'Knowledge Base', 'system'),
    ('en', 'kb_card_subtitle', 'Manage self-service articles', 'system'),
    ('en', 'kb_portal_nav_item', 'Knowledge Base', 'system'),
    ('en', 'kb_search_placeholder', 'Search the Knowledge Base…', 'system'),
    ('en', 'kb_category_all', 'All', 'system'),
    ('en', 'kb_visibility_public', 'Public', 'system'),
    ('en', 'kb_visibility_internal', 'Internal', 'system'),
    ('en', 'kb_helpful_btn', 'Helpful', 'system'),
    ('en', 'kb_not_helpful_btn', 'Not Helpful', 'system'),
    ('en', 'kb_list_empty', 'No articles found.', 'system'),
    ('en', 'kb_suggest_title', 'Before you submit — these articles might help:', 'system'),
    ('en', 'kb_suggest_helped_btn', 'Yes, this helped — don''t submit', 'system'),
    ('en', 'kb_suggest_not_helped_btn', 'None helped — submit anyway', 'system'),
    ('en', 'kb_search_panel_title', 'Search Knowledge Base', 'system'),
    ('en', 'kb_link_article_btn', 'Link to ticket', 'system'),
    ('en', 'kb_linked_articles_title', 'Linked Articles', 'system'),
    ('en', 'kb_admin_page_title', 'Knowledge Base', 'system'),
    ('en', 'kb_add_article_btn', '+ New Article', 'system'),
    ('en', 'kb_form_title_new', 'New Article', 'system'),
    ('en', 'kb_form_title_edit', 'Edit Article', 'system'),
    ('en', 'kb_form_title_label', 'Title', 'system'),
    ('en', 'kb_form_body_label', 'Body', 'system'),
    ('en', 'kb_form_category_label', 'Category', 'system'),
    ('en', 'kb_form_tags_label', 'Tags (comma-separated)', 'system'),
    ('en', 'kb_form_visibility_label', 'Visibility', 'system'),
    ('en', 'kb_col_title', 'Title', 'system'),
    ('en', 'kb_col_category', 'Category', 'system'),
    ('en', 'kb_col_visibility', 'Visibility', 'system'),
    ('en', 'kb_col_views', 'Views', 'system'),
    ('en', 'kb_delete_confirm', 'Delete this article? This cannot be undone.', 'system'),
    ('en', 'kb_generate_from_ticket_btn', 'Generate from Resolved Ticket', 'system'),
    ('en', 'kb_generate_from_ticket_title', 'Generate Article from Ticket', 'system'),
    ('en', 'kb_generate_from_ticket_pick_label', 'Pick a resolved ticket', 'system'),
    ('en', 'kb_ai_review_btn', 'AI Review', 'system'),
    ('en', 'kb_ai_review_banner', 'AI reviewed this article and proposed {{count}} change(s)', 'system'),
    ('en', 'kb_ai_review_accept_btn', 'Accept', 'system'),
    ('en', 'kb_ai_review_discard_btn', 'Discard', 'system'),
    ('en', 'kb_categories_nav_item', 'KB Categories', 'system'),
    ('en', 'kb_categories_page_title', 'Knowledge Base Categories', 'system'),
    ('en', 'kb_category_create_btn', '+ Add Category', 'system'),
    ('en', 'kb_category_name_label', 'Name', 'system'),
    ('en', 'kb_category_icon_label', 'Icon (emoji)', 'system'),
    ('en', 'kb_category_delete_confirm', 'Delete this category? Articles keep their content but lose this category.', 'system'),
    ('en', 'no_kb_categories', 'No categories defined.', 'system');
