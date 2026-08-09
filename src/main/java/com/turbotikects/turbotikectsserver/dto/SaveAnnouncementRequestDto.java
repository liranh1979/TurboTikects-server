package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class SaveAnnouncementRequestDto {
    private String severity;
    private String title;
    private String message;
    private Boolean showOnPortal;
    private Boolean showOnTicketCreate;
    private Boolean showOnAgentDashboard;
    private Boolean broadcastEmail;
    private String broadcastTarget;
    private Integer broadcastGroupId;
}
