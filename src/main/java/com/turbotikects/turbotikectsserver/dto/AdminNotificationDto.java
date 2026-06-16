package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationDto {
    private Long id;
    private Long ticketId;
    private String fieldKey;
    private String notificationType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
