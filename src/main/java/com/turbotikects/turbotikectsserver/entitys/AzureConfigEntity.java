package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "azure_configs")
@Data
public class AzureConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false, columnDefinition = "TEXT")
    private String clientSecret;

    @Column(name = "user_filter")
    private String userFilter;

    @Column(name = "group_filter")
    private String groupFilter;

    @Column(name = "user_delta_link", columnDefinition = "TEXT")
    private String userDeltaLink;

    @Column(name = "group_delta_link", columnDefinition = "TEXT")
    private String groupDeltaLink;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
