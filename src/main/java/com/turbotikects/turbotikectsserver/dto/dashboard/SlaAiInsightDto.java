package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SlaAiInsightDto {
    private String summary;
    private List<SlaFindingDto> findings;
    private boolean cached;
    private LocalDateTime generatedAt;
}
