package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListModelsRequestDto {
    @JsonProperty("provider_name")
    private String providerName;

    @JsonProperty("api_key")
    private String apiKey;
}
