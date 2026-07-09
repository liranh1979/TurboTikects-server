package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notifications")
@Data
public class UserNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
