package com.turbotikects.turbotikectsserver.dto.mcp;

import lombok.Data;

import java.util.Map;

@Data
public class McpTestToolRequestDto {
    private String toolName;
    private Map<String, Object> args;
}
