package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class WorkflowApprovalContextDto {
    private Long ticketId;
    private String ticketTitle;
    private String itemTitle;
    private String requesterName;
    private int levelOrder;
    private boolean expired;
    private boolean alreadyDecided;
    private String existingDecision; // 'approved'|'rejected', only set when alreadyDecided
}
