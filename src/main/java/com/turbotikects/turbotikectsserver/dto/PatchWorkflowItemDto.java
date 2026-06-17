package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PatchWorkflowItemDto {
    private String status;
    private Integer assignedUserId;
    private Integer assignedGroupId;
    private Map<String, Object> fieldValues;
}
