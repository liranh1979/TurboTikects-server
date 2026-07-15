package com.turbotikects.turbotikectsserver.services;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the official MCP Java SDK (io.modelcontextprotocol.sdk:mcp-core, v2.0.0) for
 * the mcp_tool workflow action item (FEAT-06 Phase 6). This app only ever acts as an MCP *client*
 * (calls out to admin-configured external MCP servers) — never as an MCP server itself, a
 * deliberate scope decision (see the FEAT-06 plan's Phase 6 section).
 *
 * Uses the Streamable HTTP transport (the current spec transport, not the legacy standalone-SSE
 * one) — matches the "admin configures a server URL" mental model this app already uses for
 * external_api, and is what modern MCP servers (including the reference "everything" server run
 * via `npx @modelcontextprotocol/server-everything streamableHttp`) speak by default.
 *
 * A fresh client is opened per top-level operation (one discovery call, or one item's whole call
 * sequence) rather than pooled/reused across requests — matches this codebase's established
 * "fresh HttpClient per call" pattern (EmailSenderService/SlaEscalationExecutor/
 * ExternalApiActionExecutor), and MCP's own initialize-handshake-per-connection model doesn't
 * lend itself to silent reuse across unrelated operations anyway.
 */
@Slf4j
@Service
public class McpClientService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /** Opens a fresh client, initializes it, and leaves it OPEN for the caller to run multiple tool calls against — the caller is responsible for calling closeGracefully() when done. */
    public McpSyncClient openClient(String serverUrl, String bearerToken) {
        McpClientTransport transport = buildTransport(serverUrl, bearerToken);
        McpSyncClient client = McpClient.sync(transport).requestTimeout(REQUEST_TIMEOUT).build();
        client.initialize();
        return client;
    }

    /** One-shot tool discovery for the Designer's "Discover Tools" action — opens, lists, closes. */
    public List<McpSchema.Tool> discoverTools(String serverUrl, String bearerToken) {
        McpSyncClient client = openClient(serverUrl, bearerToken);
        try {
            return client.listTools().tools();
        } finally {
            client.closeGracefully();
        }
    }

    public McpSchema.CallToolResult callTool(McpSyncClient client, String toolName, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(toolName).arguments(arguments).build());
    }

    private McpClientTransport buildTransport(String serverUrl, String bearerToken) {
        var builder = HttpClientStreamableHttpTransport.builder(serverUrl).endpoint("/mcp");
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.httpRequestCustomizer((b, method, endpoint, body, ctx) ->
                    b.header("Authorization", "Bearer " + bearerToken));
        }
        return builder.build();
    }
}
