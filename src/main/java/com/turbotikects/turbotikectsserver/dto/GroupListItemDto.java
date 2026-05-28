package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GroupListItemDto {
    private Long id;
    @JsonProperty("display_name")
    private String displayName;
    private Map<String, Object> metadata;
    @JsonProperty("member_count")
    private int memberCount;
    private List<String> permissions;
}
