package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GroupMemberDto {
    @JsonProperty("user_id")
    private Long userId;
    private String username;
    @JsonProperty("display_name")
    private String displayName;
}
