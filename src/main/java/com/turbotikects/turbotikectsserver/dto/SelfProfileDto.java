package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SelfProfileDto {
    private String username;
    private String displayName;
    private String email;
    private String preferredLanguage;
    private boolean canChangePassword;
    private List<SelfProfileFieldDto> fields;
}
