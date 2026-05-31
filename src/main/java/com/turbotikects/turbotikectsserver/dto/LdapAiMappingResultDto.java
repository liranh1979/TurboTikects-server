package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class LdapAiMappingResultDto {

    private List<LdapMappingSuggestionDto> mappings;

    @JsonProperty("missingFields")
    private List<LdapMissingFieldSuggestionDto> missingFields;

    @JsonProperty("hasUnmappedAttributes")
    private boolean hasUnmappedAttributes;

    private String error;
}
