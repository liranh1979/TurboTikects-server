package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "acceleration_rules")
@Data
public class AccelerationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "execution_order", nullable = false)
    private Integer executionOrder = 0;

    // JSON arrays stored as plain Strings; parsed by services via ObjectMapper
    @Column(name = "trigger_conditions", columnDefinition = "LONGTEXT", nullable = false)
    private String triggerConditions = "[]";

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String actions = "[]";

    @Column(name = "generated_script", columnDefinition = "LONGTEXT")
    private String generatedScript;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
