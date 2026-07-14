package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_sla_escalations_fired")
@Data
public class TicketSlaEscalationFiredEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "sla_escalation_step_id", nullable = false)
    private Long slaEscalationStepId;

    @Column(name = "fired_at")
    private LocalDateTime firedAt;

    @PrePersist
    void onCreate() {
        firedAt = LocalDateTime.now();
    }
}
