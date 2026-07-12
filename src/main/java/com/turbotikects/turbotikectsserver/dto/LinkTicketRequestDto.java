package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class LinkTicketRequestDto {
    private Long targetTicketId;
    private String relationshipType;
}
