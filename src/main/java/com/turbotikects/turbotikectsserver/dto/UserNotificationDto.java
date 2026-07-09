package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserNotificationDto {
    private Long id;
    private Long ticketId;
    private String notificationType;
    private String message;
    private String linkUrl;
    private boolean isRead;
    private LocalDateTime createdAt;
}
