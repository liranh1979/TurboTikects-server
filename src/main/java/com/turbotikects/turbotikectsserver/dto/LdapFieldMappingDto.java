package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LdapFieldMappingDto {

    private Long id;

    @JsonProperty("ldap_config_id")
    private Long ldapConfigId;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("ldap_attribute")
    private String ldapAttribute;

    @JsonProperty("system_field_key")
    private String systemFieldKey;
}
