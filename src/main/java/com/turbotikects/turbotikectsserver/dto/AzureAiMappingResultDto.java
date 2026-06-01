package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AzureAiMappingResultDto {

    private List<AzureMappingSuggestionDto> mappings;

    @JsonProperty("missingFields")
    private List<AzureMissingFieldSuggestionDto> missingFields;

    @JsonProperty("hasUnmappedAttributes")
    private boolean hasUnmappedAttributes;

    private String error;
}
