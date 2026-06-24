package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class DashboardAiReportDto {
    private String summary;
    private List<RecurringProblemDto> recurringProblems;
    private boolean cached;
    private LocalDateTime generatedAt;
}
