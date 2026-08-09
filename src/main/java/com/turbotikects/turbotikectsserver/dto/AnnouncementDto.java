package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnouncementDto {
    private Long id;
    private String severity;
    private String title;
    private String message;
    private Boolean showOnPortal;
    private Boolean showOnTicketCreate;
    private Boolean showOnAgentDashboard;
    private Boolean isActive;
    private Boolean broadcastEmail;
    private String broadcastTarget;
    private Integer broadcastGroupId;
    private String broadcastGroupName;
    private String createdByName;
    private LocalDateTime resolvedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
