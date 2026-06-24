package com.turbotikects.turbotikectsserver.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTimeSeriesPointDto {
    private String bucket;
    private long count;
}
