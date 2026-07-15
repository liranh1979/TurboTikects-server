package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One-click email approve/reject token, minted per approval LEVEL (a fresh token each time a new
 * level activates, since each level typically has a different approver). One-time-use enforced the
 * same way TicketCsatEntity does it — check usedAt != null rather than a separate boolean/deletion.
 */
@Entity
@Table(name = "workflow_approval_tokens")
@Data
public class WorkflowApprovalTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_approval_decision_id", nullable = false)
    private Long workflowApprovalDecisionId;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
