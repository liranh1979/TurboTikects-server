package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PermissionDto {
    private Long id;
    @JsonProperty("permission_key")
    private String permissionKey;
    @JsonProperty("display_order")
    private int displayOrder;
}
