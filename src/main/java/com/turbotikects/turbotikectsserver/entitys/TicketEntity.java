package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tickets")
@Data
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "template_version_id", nullable = false)
    private Long templateVersionId;

    @Column(name = "request_user_id", nullable = false)
    private Integer requestUserId;

    @Column(name = "responsible_user_id")
    private Integer responsibleUserId;

    @Column(name = "responsible_group_id")
    private Integer responsibleGroupId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ticket_data", columnDefinition = "json")
    private Map<String, Object> ticketData;

    @Column(nullable = false)
    private int version;

    @Column(name = "source_type", length = 32)
    private String sourceType = "manual";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (version == 0) version = 1;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
