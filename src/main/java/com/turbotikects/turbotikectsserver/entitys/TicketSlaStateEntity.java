package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_sla_state")
@Data
public class TicketSlaStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Column(name = "sla_policy_id")
    private Long slaPolicyId;

    @Column(name = "first_response_target_at")
    private LocalDateTime firstResponseTargetAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "first_response_breached", nullable = false)
    private boolean firstResponseBreached = false;

    @Column(name = "resolution_target_at")
    private LocalDateTime resolutionTargetAt;

    @Column(name = "resolution_at")
    private LocalDateTime resolutionAt;

    @Column(name = "resolution_breached", nullable = false)
    private boolean resolutionBreached = false;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "total_paused_seconds", nullable = false)
    private long totalPausedSeconds = 0;

    @Column(name = "ai_breach_risk_score")
    private Integer aiBreachRiskScore;

    @Column(name = "ai_risk_reason", columnDefinition = "TEXT")
    private String aiRiskReason;

    @Column(name = "ai_last_scored_at")
    private LocalDateTime aiLastScoredAt;

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
