package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TicketListItemDto {
    private Long id;
    private String title;
    private String status;
    private String priority;
    private Long templateId;
    private Long templateVersionId;
    private String templateName;
    private Integer requestUserId;
    private String requestUserDisplayName;
    private Integer responsibleUserId;
    private String responsibleUserDisplayName;
    private Integer responsibleGroupId;
    private List<TicketLabelDto> labels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int version;
    private Map<String, Object> ticketData;
}
