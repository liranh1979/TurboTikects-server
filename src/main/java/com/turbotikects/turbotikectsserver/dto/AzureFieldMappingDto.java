package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AzureFieldMappingDto {

    private Long id;

    @JsonProperty("azure_config_id")
    private Long azureConfigId;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("azure_attribute")
    private String azureAttribute;

    @JsonProperty("system_field_key")
    private String systemFieldKey;
}
