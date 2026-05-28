package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UpdateGroupDto {
    private String displayName;
    private Map<String, Object> metadata;
    private List<String> permissions; // null = no change; super admin only
}
