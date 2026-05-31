package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LdapMappingSuggestionDto {

    @JsonProperty("ldapAttribute")
    private String ldapAttribute;

    @JsonProperty("ldapSampleValue")
    private String ldapSampleValue;

    @JsonProperty("systemFieldKey")
    private String systemFieldKey;

    private String confidence;
    private String reasoning;
}
