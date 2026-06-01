package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AzureMappingSuggestionDto {

    @JsonProperty("azureAttribute")
    private String azureAttribute;

    @JsonProperty("azureSampleValue")
    private String azureSampleValue;

    @JsonProperty("systemFieldKey")
    private String systemFieldKey;

    private String confidence;
    private String reasoning;
}
