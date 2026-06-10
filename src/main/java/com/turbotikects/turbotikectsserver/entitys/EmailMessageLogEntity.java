package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_message_log")
@Data
public class EmailMessageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mailbox_id", nullable = false)
    private Long mailboxId;

    @Column(name = "message_id", nullable = false, length = 500)
    private String messageId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction = "inbound";

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
