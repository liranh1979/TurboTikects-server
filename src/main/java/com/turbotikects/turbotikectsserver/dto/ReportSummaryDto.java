package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReportSummaryDto {
    private String summary;
    private List<ReportTipDto> tips;
}
