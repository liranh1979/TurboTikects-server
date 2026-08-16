package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportRunDto {
    private Long id;
    private Long reportDefinitionId;
    private String triggeredBy;
    private Integer rowCount;
    private String status;
    private String aiSummary;
    private List<Object> aiTips;
    private String csvPath;
    private String pdfPath;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
