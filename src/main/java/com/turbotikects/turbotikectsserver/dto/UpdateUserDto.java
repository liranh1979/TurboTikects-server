package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateUserDto {
    private String displayName;
    private String password; // raw — will be hashed server-side; null = no change
    private Map<String, Object> metadata;
}