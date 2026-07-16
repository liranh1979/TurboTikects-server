package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "action_item_library")
@Data
public class ActionItemLibraryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // task | external_api | mcp_tool — same vocabulary as WorkflowItemEntity.type. Approval items
    // stay out of this library for now; they're still configured inline in the Designer.
    @Column(nullable = false, length = 32)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_config", columnDefinition = "json")
    private Map<String, Object> typeConfig;

    // manual (built via the "+ New Simple Action Item" form) | ai (built via the AI Workflow
    // Builder) — drives the Simple/AI badge in the library list UI.
    @Column(nullable = false, length = 16)
    private String source = "manual";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
