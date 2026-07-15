package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.McpDiscoverToolsRequestDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.McpClientService;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** FEAT-06 Phase 6 — powers the Workflow Designer's "Discover Tools" action for mcp_tool items. */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    private final McpClientService mcpClientService;

    public McpController(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/discover-tools")
    public List<Map<String, Object>> discoverTools(@RequestBody McpDiscoverToolsRequestDto dto) {
        if (dto.getServerUrl() == null || dto.getServerUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serverUrl is required");
        }
        List<McpSchema.Tool> tools;
        try {
            tools = mcpClientService.discoverTools(dto.getServerUrl(), dto.getToken());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to MCP server: " + e.getMessage());
        }
        return tools.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("inputSchema", t.inputSchema());
            return m;
        }).collect(Collectors.toList());
    }
}
