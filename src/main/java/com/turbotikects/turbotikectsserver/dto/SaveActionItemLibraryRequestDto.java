package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SaveActionItemLibraryRequestDto {
    private String name;
    private String type;
    private Map<String, Object> typeConfig;
    private String source;
}
