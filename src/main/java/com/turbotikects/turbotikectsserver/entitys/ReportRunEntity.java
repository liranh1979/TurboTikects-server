package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "report_runs")
@Data
public class ReportRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_definition_id", nullable = false)
    private Long reportDefinitionId;

    // "manual" (Test button) or "scheduler"
    @Column(name = "triggered_by", nullable = false, length = 16)
    private String triggeredBy;

    @Column(name = "row_count")
    private Integer rowCount;

    // "success" | "no_data" | "failed"
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    // [{"description": string, "confidencePercent": number}] — already filtered to >= 60%.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_tips", columnDefinition = "json")
    private List<Object> aiTips;

    @Column(name = "csv_path", length = 512)
    private String csvPath;

    @Column(name = "pdf_path", length = 512)
    private String pdfPath;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
    }
}
