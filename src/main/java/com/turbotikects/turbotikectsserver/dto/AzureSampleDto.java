package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AzureSampleDto {

    private Map<String, String> attributes;
    private String error;
}
