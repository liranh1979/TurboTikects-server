package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UserListItemDto {
    private Long id;
    private String username;
    @JsonProperty("display_name")
    private String displayName;
    @JsonProperty("is_super_admin")
    private boolean superAdmin;
    private Map<String, Object> metadata;
    @JsonProperty("personal_permissions")
    private List<String> personalPermissions;
}