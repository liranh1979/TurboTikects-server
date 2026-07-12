package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class MergeTicketRequestDto {
    private Long targetTicketId;
    private boolean notifyRequester;
    private boolean addComment;
    private boolean moveAttachments;
}
