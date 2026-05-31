package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SaveLdapMappingsRequestDto {

    @JsonProperty("entity_type")
    private String entityType;

    private List<LdapFieldMappingDto> mappings;
}
