package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class GroupListItemDto {
    private Long id;
    @JsonProperty("display_name")
    private String displayName;
    private Map<String, Object> metadata;
}
