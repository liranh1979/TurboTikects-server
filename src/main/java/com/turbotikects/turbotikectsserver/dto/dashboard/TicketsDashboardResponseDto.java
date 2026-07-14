package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketsDashboardResponseDto {
    private DashboardSeriesDto tickets;
    private DashboardSeriesDto actionItems;
    private DashboardAiReportDto aiReport;
    private CsatDashboardResponseDto csat;
    private SlaDashboardResponseDto sla;
}
