package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiChatMessageDto {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
