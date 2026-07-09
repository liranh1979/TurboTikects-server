package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CsatLowScoreTicketDto {
    private Long ticketId;
    private String title;
    private String agent;
    private int score;
    private String comment;
}
