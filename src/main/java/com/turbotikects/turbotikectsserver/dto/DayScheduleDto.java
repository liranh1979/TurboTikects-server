package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DayScheduleDto {
    @JsonProperty("day_of_week") private int dayOfWeek;   // 0=Sun … 6=Sat
    @JsonProperty("is_open")     private boolean open;
    @JsonProperty("slot1_start") private String slot1Start;
    @JsonProperty("slot1_end")   private String slot1End;
    @JsonProperty("slot2_start") private String slot2Start;
    @JsonProperty("slot2_end")   private String slot2End;
}
