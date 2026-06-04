package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UserSelectItemDto {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("display_name")
    private String displayName;
}
