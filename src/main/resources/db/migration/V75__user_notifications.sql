-- Generic per-user notification inbox (distinct from the existing admin-only
-- admin_notifications table, which has no recipient concept). CSAT surveys
-- are the first consumer; future end-user-facing notifications reuse this too.

CREATE TABLE IF NOT EXISTS user_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id INT NOT NULL,
    ticket_id BIGINT NULL,
    notification_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    link_url VARCHAR(500) NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipient_user_id) REFERENCES users(red_id) ON DELETE CASCADE,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);
