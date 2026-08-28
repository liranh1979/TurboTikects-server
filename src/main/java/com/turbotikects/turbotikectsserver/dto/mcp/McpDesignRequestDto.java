package com.turbotikects.turbotikectsserver.dto.mcp;

import lombok.Data;

@Data
public class McpDesignRequestDto {
    private Long sessionId; // null on first call — a new AiChatSession is created
    private String baseUrl;
    private String docs;
}
