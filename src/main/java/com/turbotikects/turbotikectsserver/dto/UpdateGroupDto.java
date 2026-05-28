package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateGroupDto {
    private String displayName;
    private Map<String, Object> metadata;
}
