package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
@Data
public class AnnouncementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String severity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "show_on_portal", nullable = false)
    private Boolean showOnPortal = true;

    @Column(name = "show_on_ticket_create", nullable = false)
    private Boolean showOnTicketCreate = false;

    @Column(name = "show_on_agent_dashboard", nullable = false)
    private Boolean showOnAgentDashboard = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "broadcast_email", nullable = false)
    private Boolean broadcastEmail = false;

    @Column(name = "broadcast_target", length = 16)
    private String broadcastTarget;

    @Column(name = "broadcast_group_id")
    private Integer broadcastGroupId;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
