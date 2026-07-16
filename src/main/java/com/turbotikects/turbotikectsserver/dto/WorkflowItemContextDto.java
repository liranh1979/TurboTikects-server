package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

// Deliberately minimal — used to let a user assigned to one workflow item (but not the ticket's
// requester or a TICKET_MANAGER) see just enough context to act on it, without exposing the rest
// of the ticket's data. See WorkflowService.getItemContext/assertCanViewItem.
@Data
public class WorkflowItemContextDto {
    private Long itemId;
    private Long ticketId;
    private String ticketTitle;
    private String requesterName;
    private String itemTitle;
    private String itemType;
    private String itemStatus;
    // Only itemType=='task's own mini-field definitions matter here — {fields: [{key,label,fieldType,...}]}.
    // Carried straight through from the Action Item Library entry this node was copied from.
    private Map<String, Object> typeConfig;
    private Map<String, Object> fieldValues;
}
