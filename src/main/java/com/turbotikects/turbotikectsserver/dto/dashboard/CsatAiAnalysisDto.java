package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CsatAiAnalysisDto {
    private String summary;
    private List<CsatImprovementPointDto> improvementPoints;
    private boolean cached;
    private LocalDateTime generatedAt;
}
