package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class TicketSseEventDto {
    private String type;
    private Long ticketId;
    private Integer userId;
    private String displayName;
    private Integer newVersion;
    private List<String> changedFields;
    private String operation;
}
