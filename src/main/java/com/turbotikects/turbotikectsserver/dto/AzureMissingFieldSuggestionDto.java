package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AzureMissingFieldSuggestionDto {

    @JsonProperty("azureAttribute")
    private String azureAttribute;

    @JsonProperty("suggestedFieldKey")
    private String suggestedFieldKey;

    @JsonProperty("suggestedLabel")
    private String suggestedLabel;

    @JsonProperty("suggestedFieldType")
    private String suggestedFieldType;
}
