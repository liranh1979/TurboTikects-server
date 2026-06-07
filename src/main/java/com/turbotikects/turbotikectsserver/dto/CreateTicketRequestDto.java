package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateTicketRequestDto {
    private String title;
    private String description;
    private String status;
    private Long templateId;
    private Long templateVersionId;
    private Integer requestUserId;
    private Integer responsibleUserId;
    private Integer responsibleGroupId;
    private Map<String, Object> ticketData;
    private List<Long> labelIds;
}
