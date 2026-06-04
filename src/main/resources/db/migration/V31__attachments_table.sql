-- Physical file store — one row per unique file content (dedup by SHA-256)
CREATE TABLE IF NOT EXISTS attachment_files (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    content_hash    VARCHAR(64)  NOT NULL UNIQUE,
    stored_filename VARCHAR(500) NOT NULL UNIQUE,
    mime_type       VARCHAR(128) NOT NULL,
    file_size       BIGINT       NOT NULL,
    ref_count       INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Logical attachment — one row per entity-file association
CREATE TABLE IF NOT EXISTS attachments (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    entity_type       VARCHAR(32)  NOT NULL,
    entity_id         BIGINT       NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_id           BIGINT       NOT NULL,
    uploaded_by       INT          NULL,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_entity (entity_type, entity_id),
    CONSTRAINT fk_attachment_file
        FOREIGN KEY (file_id) REFERENCES attachment_files(id),
    CONSTRAINT fk_attachment_uploader
        FOREIGN KEY (uploaded_by) REFERENCES users(red_id) ON DELETE SET NULL
);
