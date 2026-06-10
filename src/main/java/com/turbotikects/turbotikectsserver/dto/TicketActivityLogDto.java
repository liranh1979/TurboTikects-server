package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TicketActivityLogDto {
    private Long id;
    private Long ticketId;
    private Integer actorId;
    private String actorDisplayName;
    private String operation;
    private String activityType;
    private Map<String, Object> changes;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
