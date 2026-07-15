package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TicketsDashboardResponseDto {
    private DashboardSeriesDto tickets;
    private DashboardSeriesDto actionItems;
    private DashboardAiReportDto aiReport;
    private CsatDashboardResponseDto csat;
    private SlaDashboardResponseDto sla;
    private List<String> sectionOrder;
}
