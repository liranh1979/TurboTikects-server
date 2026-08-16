package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "report_definitions")
@Data
public class ReportDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType = "ticket";

    // { "selectedFields": [...], "conditions": {combinator, conditions:[...]} } —
    // same AND/OR leaf shape ReportQueryCompiler shares with AccelerationSpecificationBuilder.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_spec", nullable = false, columnDefinition = "json")
    private Map<String, Object> querySpec;

    // e.g. ["csv","pdf"]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "export_formats", nullable = false, columnDefinition = "json")
    private List<String> exportFormats;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "ai_generated", nullable = false)
    private Boolean aiGenerated = false;

    @Column(name = "last_ai_prompt", columnDefinition = "TEXT")
    private String lastAiPrompt;

    @Column(name = "created_by")
    private Integer createdBy;

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
