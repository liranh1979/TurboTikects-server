package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class AiChatCreateSessionDto {
    private String sessionType;
    private Long ticketId;
}
