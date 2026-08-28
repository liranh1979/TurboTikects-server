package com.turbotikects.turbotikectsserver.dto.mcp;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class McpGenerateScriptRequestDto {
    private Long sessionId; // required — must continue the session proposeDesign created
    private List<Map<String, Object>> approvedTools;
    private McpAuthShapeDto auth;
}
