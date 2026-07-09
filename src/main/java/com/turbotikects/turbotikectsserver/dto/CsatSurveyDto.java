package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class CsatSurveyDto {
    private Long ticketId;
    private String ticketTitle;
    private String resolvedBy;
    private String resolutionTime; // human-readable duration, e.g. "4h 12m"
    private boolean alreadyResponded;
    private boolean expired;
    private Integer existingScore;
    private String existingComment;
}
