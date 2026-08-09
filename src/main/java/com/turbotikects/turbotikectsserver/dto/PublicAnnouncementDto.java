package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicAnnouncementDto {
    private Long id;
    private String severity;
    private String severityLabel;
    private String severityColor;
    private String severityIcon;
    private String title;
    private String message;
    private Boolean showOnPortal;
    private Boolean showOnTicketCreate;
    private Boolean showOnAgentDashboard;
    private String createdByName;
    private LocalDateTime createdAt;
}
