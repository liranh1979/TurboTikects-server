package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LdapMissingFieldSuggestionDto {

    @JsonProperty("ldapAttribute")
    private String ldapAttribute;

    @JsonProperty("suggestedFieldKey")
    private String suggestedFieldKey;

    @JsonProperty("suggestedLabel")
    private String suggestedLabel;

    @JsonProperty("suggestedFieldType")
    private String suggestedFieldType;
}
