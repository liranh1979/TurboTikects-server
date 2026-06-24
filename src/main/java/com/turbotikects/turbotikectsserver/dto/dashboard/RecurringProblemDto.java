package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecurringProblemDto {
    private String description;
    private int confidencePercent;
    private String solution; // null when confidencePercent < 60 — enforced server-side, never trust the model alone
}
