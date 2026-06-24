package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateOwnProfileDto {
    private String displayName;
    private String email;
    private String preferredLanguage;
    private Map<String, String> metadata;
}
