package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DashboardSeriesDto {
    private List<DashboardTimeSeriesPointDto> day;
    private List<DashboardTimeSeriesPointDto> week;
    private List<DashboardTimeSeriesPointDto> month;
    private Map<String, Long> byStatus;
}
