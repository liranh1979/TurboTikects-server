package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One row per approval *level* on a workflow item of type 'approval' (see WorkflowItemEntity.type).
 * The item's typeConfig (set at template-design time) holds the admin-authored level definitions
 * (approver, timeout); this table holds the runtime state of walking through them — level_order
 * matches the index into typeConfig.levels. approverUserId/approverGroupId is who's *allowed* to
 * decide (copied from the level config at activation time); decidedByUserId is who actually did,
 * once decided (meaningful when approverGroupId is set — any member of the group can act).
 */
@Entity
@Table(name = "workflow_approval_decisions")
@Data
public class WorkflowApprovalDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_item_id", nullable = false)
    private Long workflowItemId;

    @Column(name = "level_order", nullable = false)
    private Integer levelOrder;

    @Column(name = "approver_user_id")
    private Integer approverUserId;

    @Column(name = "approver_group_id")
    private Integer approverGroupId;

    // pending | approved | rejected | escalated
    @Column(nullable = false, length = 16)
    private String decision = "pending";

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by_user_id")
    private Integer decidedByUserId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
