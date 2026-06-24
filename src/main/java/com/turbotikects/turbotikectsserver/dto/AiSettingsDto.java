package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class AiSettingsDto {


    private Long id;

    @JsonProperty("provider_name")
    private String providerName;


    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("model_name")
    private String modelName;


    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("is_system")
    private boolean isSystem;


    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
