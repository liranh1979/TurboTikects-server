package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class HoursOfOperationDto {
    private List<DayScheduleDto> days;
    private String timezone;
}
