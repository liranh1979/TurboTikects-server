package com.turbotikects.turbotikectsserver.services;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
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

    /**
     * Opens a fresh client, initializes it, and leaves it OPEN for the caller to run multiple tool
     * calls against — the caller is responsible for calling closeGracefully() when done.
     * @param authType "bearer" sends "Authorization: Bearer &lt;token&gt;"; "api_key" sends the
     *                 token under a custom header (headerName, default "X-API-Key") — needed
     *                 because not every real-world MCP server accepts bearer auth (e.g. Google's
     *                 Travel Impact Model MCP requires a plain "X-Goog-Api-Key" header). Any other
     *                 value (including null/"none") sends no auth header at all.
     */
    public McpSyncClient openClient(String serverUrl, String authType, String headerName, String token) {
        McpClientTransport transport = buildTransport(serverUrl, authType, headerName, token);
        McpSyncClient client = McpClient.sync(transport).requestTimeout(REQUEST_TIMEOUT).build();
        client.initialize();
        return client;
    }

    /** One-shot tool discovery for the Designer's "Discover Tools" action — opens, lists, closes. */
    public List<McpSchema.Tool> discoverTools(String serverUrl, String authType, String headerName, String token) {
        McpSyncClient client = openClient(serverUrl, authType, headerName, token);
        try {
            return client.listTools().tools();
        } finally {
            client.closeGracefully();
        }
    }

    public McpSchema.CallToolResult callTool(McpSyncClient client, String toolName, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(toolName).arguments(arguments).build());
    }

    /**
     * A real bug found live against two different real MCP servers: the SDK's transport resolves
     * requests as {@code baseUri.resolve(endpoint)}, and since "/mcp" (an absolute-path reference)
     * REPLACES the base URI's own path entirely per standard URI resolution rules, hardcoding
     * {@code .endpoint("/mcp")} silently discarded whatever path the admin actually typed into
     * serverUrl whenever it wasn't exactly "/mcp" — e.g. "https://host/mcp-echo-server" got rewritten
     * to "https://host/mcp" (a different, wrong route) rather than requesting the server the admin
     * actually pointed at. Fixed by splitting serverUrl ourselves into an origin (scheme+authority,
     * passed as the SDK's baseUri) and its own path+query (passed as the SDK's endpoint verbatim,
     * so nothing gets silently replaced) — falling back to the SDK's own "/mcp" convention only when
     * serverUrl has no path of its own (e.g. a bare "https://mcp.example.com" host, matching this
     * app's own placeholder text). Verified against two real servers: Google's Travel Impact Model
     * MCP ("https://travelimpactmodel.googleapis.com/mcp", worked before only by coincidence since
     * its own path already happened to be "/mcp") and a real third-party echo server whose path is
     * NOT "/mcp" (previously broken, confirmed fixed after this change).
     */
    private McpClientTransport buildTransport(String serverUrl, String authType, String headerName, String token) {
        URI parsed = URI.create(serverUrl.trim());
        String origin = parsed.getScheme() + "://" + parsed.getAuthority();
        String path = parsed.getRawPath();
        String resolvedEndpoint = (path == null || path.isBlank() || path.equals("/")) ? "/mcp" : path;
        if (parsed.getRawQuery() != null && !parsed.getRawQuery().isBlank()) {
            resolvedEndpoint = resolvedEndpoint + "?" + parsed.getRawQuery();
        }

        var builder = HttpClientStreamableHttpTransport.builder(origin).endpoint(resolvedEndpoint);
        if (token != null && !token.isBlank()) {
            if ("api_key".equals(authType)) {
                String header = (headerName != null && !headerName.isBlank()) ? headerName : "X-API-Key";
                builder.httpRequestCustomizer((b, method, endpoint, body, ctx) -> b.header(header, token));
            } else {
                // Default/legacy behavior: any non-"api_key" auth with a token present is bearer.
                builder.httpRequestCustomizer((b, method, endpoint, body, ctx) ->
                        b.header("Authorization", "Bearer " + token));
            }
        }
        return builder.build();
    }
}
