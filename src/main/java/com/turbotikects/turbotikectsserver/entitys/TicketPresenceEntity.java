package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_presence")
@Data
@IdClass(TicketPresenceEntity.TicketPresenceId.class)
public class TicketPresenceEntity {

    @Id
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Data
    public static class TicketPresenceId implements Serializable {
        private Long ticketId;
        private Integer userId;
    }
}
