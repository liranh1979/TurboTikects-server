package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiChatSessionDto {
    private Long id;
    private Integer userId;
    private Long ticketId;
    private String sessionType;
    private String status;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AiChatMessageDto> messages;
}
