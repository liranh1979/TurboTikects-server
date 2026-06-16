package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PatchTicketRequestDto {
    private String title;
    private String description;
    private String status;
    private Integer requestUserId;
    private Integer responsibleUserId;
    private Integer responsibleGroupId;
    private Map<String, Object> ticketData;
    private List<Long> labelIds;
    private int version;
}
