package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CsatDashboardResponseDto {
    private Double avgScore;
    private Double responseRate;
    private Double lowScorePercent;
    private Map<Integer, Long> distribution;
    private List<CsatLowScoreTicketDto> lowScoreTickets;
    private CsatAiAnalysisDto aiAnalysis;
}
