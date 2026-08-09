package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_types")
@Data
public class AlertTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_key", nullable = false, unique = true, length = 64)
    private String typeKey;

    @Column(nullable = false, length = 16)
    private String color = "info";

    @Column(length = 8)
    private String icon;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

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
