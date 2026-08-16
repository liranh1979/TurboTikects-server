package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "report_schedules")
@Data
public class ReportScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_definition_id", nullable = false, unique = true)
    private Long reportDefinitionId;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression;

    @Column(name = "frequency_type", nullable = false, length = 16)
    private String frequencyType;

    // Either this OR recipientGroupId is set — never both, never neither (validated in ReportService).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipient_user_ids", columnDefinition = "json")
    private List<Integer> recipientUserIds;

    @Column(name = "recipient_group_id")
    private Integer recipientGroupId;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

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
