package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class MergeResultDto {
    private TicketDetailDto sourceTicket;
    private TicketDetailDto targetTicket;
}
