package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sla_escalation_steps")
@Data
public class SlaEscalationStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sla_policy_id", nullable = false)
    private Long slaPolicyId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "trigger_value")
    private BigDecimal triggerValue;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(name = "target_user_id")
    private Integer targetUserId;

    @Column(name = "target_group_id")
    private Integer targetGroupId;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
