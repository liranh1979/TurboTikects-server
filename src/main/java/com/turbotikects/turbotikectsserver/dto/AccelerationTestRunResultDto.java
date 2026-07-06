package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AccelerationTestRunResultDto {
    private boolean mutated;
    private Map<String, Object> diff;
}
