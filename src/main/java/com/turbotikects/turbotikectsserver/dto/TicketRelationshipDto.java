package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketRelationshipDto {
    private Long id;
    private String relationshipType;
    private Long otherTicketId;
    private String otherTicketTitle;
    private String otherTicketStatus;
    private LocalDateTime createdAt;
}
