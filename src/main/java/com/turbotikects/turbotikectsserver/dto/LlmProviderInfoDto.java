package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LlmProviderInfoDto {
    private String name;
    private String label;
    @JsonProperty("model_hint")
    private String modelHint;
}
