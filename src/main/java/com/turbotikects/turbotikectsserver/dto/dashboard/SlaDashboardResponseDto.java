package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SlaDashboardResponseDto {
    private List<SlaPriorityStatDto> priorityStats;
    private SlaAiInsightDto aiInsight;
}
