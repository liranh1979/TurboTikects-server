package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UpdateUserDto {
    private String displayName;
    private String password; // raw — will be hashed server-side; null = no change
    private String email;
    private Map<String, Object> metadata;
    private List<String> permissions; // null = no change; super admin only
}