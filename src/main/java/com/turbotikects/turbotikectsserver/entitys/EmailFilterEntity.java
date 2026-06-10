package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_filters")
@Data
public class EmailFilterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mailbox_id")
    private Long mailboxId;

    @Column(name = "list_type", nullable = false, length = 16)
    private String listType;

    @Column(name = "email_pattern", nullable = false, length = 255)
    private String emailPattern;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
