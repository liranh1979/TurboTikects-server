package com.turbotikects.turbotikectsserver.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UserDto {

    @JsonProperty("red_id")
    private Long userId;

    @JsonProperty("user_name")
    private String username;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("is_super_admin")
    private boolean isSuperAdmin;

    @JsonProperty("user_metadata")
    private Map<String, Object> metadata;

    @JsonProperty("effective_permissions")
    private List<String> effectivePermissions;
}
