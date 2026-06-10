-- MANAGE_EMAIL permission
INSERT IGNORE INTO permissions (permission_key, display_order) VALUES ('MANAGE_EMAIL', 9);

-- Add email field to users for sender matching
ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL;

-- Add source_type to tickets (manual, email, api)
ALTER TABLE tickets ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'manual';

-- Mailbox configuration table
CREATE TABLE IF NOT EXISTS email_mailboxes (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  display_name             VARCHAR(100) NOT NULL,
  email_address            VARCHAR(255) NOT NULL,
  protocol_type            ENUM('IMAP_SMTP','OAUTH2_GMAIL','OAUTH2_MICROSOFT') NOT NULL,
  -- IMAP/SMTP fields
  imap_host                VARCHAR(255),
  imap_port                INT,
  imap_ssl                 TINYINT(1) DEFAULT 1,
  smtp_host                VARCHAR(255),
  smtp_port                INT,
  smtp_ssl                 TINYINT(1) DEFAULT 1,
  username                 VARCHAR(255),
  password_encrypted       TEXT,
  -- OAuth2 fields
  oauth2_client_id         VARCHAR(255),
  oauth2_client_secret_enc TEXT,
  oauth2_tenant_id         VARCHAR(255),
  oauth2_access_token_enc  TEXT,
  oauth2_refresh_token_enc TEXT,
  oauth2_token_expiry      DATETIME,
  -- Status flags
  can_receive              TINYINT(1) DEFAULT 1,
  can_send                 TINYINT(1) DEFAULT 1,
  is_default_sender        TINYINT(1) DEFAULT 0,
  is_active                TINYINT(1) DEFAULT 1,
  -- Processing settings
  poll_interval_seconds    INT DEFAULT 300,
  ai_enabled               TINYINT(1) DEFAULT 0,
  ai_confidence_threshold  INT DEFAULT 60,
  unknown_sender_action    ENUM('create_user','ignore') DEFAULT 'ignore',
  ignore_signature         TINYINT(1) DEFAULT 1,
  last_checked_at          DATETIME,
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Blacklist / whitelist filters per mailbox
CREATE TABLE IF NOT EXISTS email_filters (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  mailbox_id     BIGINT,
  list_type      ENUM('blacklist','whitelist') NOT NULL,
  email_pattern  VARCHAR(255) NOT NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (mailbox_id) REFERENCES email_mailboxes(id) ON DELETE CASCADE
);

-- Processed message log (dedup + ticket linking)
CREATE TABLE IF NOT EXISTS email_message_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  mailbox_id      BIGINT NOT NULL,
  message_id      VARCHAR(500) NOT NULL,
  ticket_id       BIGINT,
  direction       ENUM('inbound','outbound') NOT NULL DEFAULT 'inbound',
  processed_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_message_id (message_id),
  FOREIGN KEY (mailbox_id) REFERENCES email_mailboxes(id) ON DELETE CASCADE,
  FOREIGN KEY (ticket_id)  REFERENCES tickets(id) ON DELETE SET NULL
);
