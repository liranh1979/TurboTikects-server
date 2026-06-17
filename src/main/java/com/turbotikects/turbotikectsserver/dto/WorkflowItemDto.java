package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkflowItemDto {
    private Long id;
    private Long ticketId;
    private String templateNodeId;
    private Long parentItemId;
    private String title;
    private String status;
    private Integer assignedUserId;
    private String assignedUserDisplayName;
    private Integer assignedGroupId;
    private Map<String, Object> fieldValues;
    private int displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
