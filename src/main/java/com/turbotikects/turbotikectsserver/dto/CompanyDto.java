package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CompanyDto {
    private Integer id;
    private String name;
    private String description;
    private String timezone;
    @JsonProperty("user_count")  private long userCount;
    @JsonProperty("group_count") private long groupCount;
}
