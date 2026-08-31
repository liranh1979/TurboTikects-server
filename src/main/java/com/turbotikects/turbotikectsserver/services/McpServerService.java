package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.mcp.*;
import com.turbotikects.turbotikectsserver.entitys.McpServerEntity;
import com.turbotikects.turbotikectsserver.repositorys.McpServerRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * FEAT-19 — the one genuinely new OS-level capability in this codebase: writing an AI-generated
 * Python script to disk, provisioning a dedicated venv, pip-installing its declared dependencies,
 * and spawning/supervising it as a local process on its own port. Deliberately no auto-restart on
 * crash (locked decision — see V2/mcp-server-management/00-index.html): a dead process is marked
 * ERROR and stays that way until an admin explicitly restarts or fixes it.
 */
@Slf4j
@Service
public class McpServerService {

    private final McpServerRepository repo;
    private final McpServerRegistry registry;
    private final McpPortAllocator portAllocator;
    private final AesEncryptionUtils aes;
    private final McpClientService mcpClientService;
    private final McpWrapperAiService aiService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.mcp.scripts-path:./mcp-scripts}")
    private String scriptsPathProp;

    /** Default "python" for Windows dev; override to "python3" via MCP_PYTHON_EXECUTABLE in the
     * Alpine Docker prod image, which has no bare "python" symlink. */
    @Value("${app.mcp.python-executable:python}")
    private String pythonExecutable;

    @Value("${app.mcp.pip-install-timeout-seconds:120}")
    private int pipInstallTimeoutSeconds;

    @Value("${app.mcp.process-startup-grace-ms:1500}")
    private long startupGraceMs;

    private Path scriptsRoot;

    public McpServerService(McpServerRepository repo, McpServerRegistry registry, McpPortAllocator portAllocator,
                             AesEncryptionUtils aes, McpClientService mcpClientService, McpWrapperAiService aiService) {
        this.repo = repo;
        this.registry = registry;
        this.portAllocator = portAllocator;
        this.aes = aes;
        this.mcpClientService = mcpClientService;
        this.aiService = aiService;
    }

    @PostConstruct
    void init() throws IOException {
        scriptsRoot = Paths.get(scriptsPathProp).toAbsolutePath().normalize();
        Files.createDirectories(scriptsRoot);
    }

    /** Stops every tracked process on graceful shutdown, so a normal restart doesn't leave
     * orphans — an unclean kill (e.g. `docker kill`) can still leave one, which is why boot-time
     * reconciliation matters too (see McpServerBootstrapper). */
    @PreDestroy
    void shutdown() {
        for (Map.Entry<Long, McpServerRegistry.RunningMcpServer> e : registry.all().entrySet()) {
            stopProcessOnly(e.getValue());
        }
    }

    // ── CRUD ───────────────────────────────────────────────────────────────────

    public List<McpServerDto> list() {
        return repo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public McpServerDto get(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public McpServerDto create(McpServerCreateDto dto, Integer userId) {
        validateCreate(dto);
        McpServerEntity entity = new McpServerEntity();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setTargetApiBaseUrl(dto.getTargetApiBaseUrl());
        entity.setTargetApiDocs(dto.getTargetApiDocs());
        entity.setTargetApiAuth(serializeAuth(dto.getAuth()));
        entity.setToolDesignJson(dto.getToolDesignJson());
        entity.setScriptContent(dto.getScriptContent());
        entity.setDependencies(dto.getDependencies());
        entity.setAiChatSessionId(dto.getAiChatSessionId());
        entity.setEnabled(true);
        entity.setSystem(false);
        entity.setPort(portAllocator.allocate());
        entity = repo.save(entity);
        deployAndStart(entity);
        return toDto(entity);
    }

    @Transactional
    public McpServerDto update(Long id, McpServerUpdateDto dto) {
        McpServerEntity entity = findOrThrow(id);
        boolean scriptChanged = dto.getScriptContent() != null && !dto.getScriptContent().equals(entity.getScriptContent());
        boolean depsChanged = dto.getDependencies() != null && !dto.getDependencies().equals(entity.getDependencies());
        boolean enablingNow = dto.getEnabled() != null && dto.getEnabled() && !entity.isEnabled();
        boolean disablingNow = dto.getEnabled() != null && !dto.getEnabled() && entity.isEnabled();

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getScriptContent() != null) entity.setScriptContent(dto.getScriptContent());
        if (dto.getDependencies() != null) entity.setDependencies(dto.getDependencies());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        entity = repo.save(entity);

        if (disablingNow) {
            stopInternal(entity.getId());
        } else if (scriptChanged || depsChanged || enablingNow) {
            deployAndStart(entity);
        }
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        McpServerEntity entity = findOrThrow(id);
        if (entity.isSystem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Built-in MCP servers cannot be deleted.");
        }
        if ("generated".equals(entity.getServerKind())) {
            stopInternal(id);
            registry.remove(id);
            deleteScriptDir(id);
        }
        repo.delete(entity);
    }

    public void start(Long id) {
        deployAndStart(findOrThrow(id));
    }

    public void stop(Long id) {
        findOrThrow(id);
        stopInternal(id);
    }

    public void restart(Long id) {
        McpServerEntity entity = findOrThrow(id);
        stopInternal(id);
        deployAndStart(entity);
    }

    // ── TEST & VERIFY (Step 5) ────────────────────────────────────────────────

    /**
     * Read-only design-vs-deployed check, reachable by anyone who can already build workflows
     * (MANAGE_FIELDS, not super-admin) — a real gap found live: the Action Item builder's
     * "Built-in Server" picker (McpServerConnectionEditor) discovers tools via the older, generic
     * /mcp/discover-tools endpoint, which has no concept of this server's original approved
     * design, so it never surfaced findDesignMismatches' warning at all — only this app's own MCP
     * Servers management wizard did, which the workflow-builder admin may never visit. Degrades to
     * an empty list (not an error) if the server can't be reached — connectivity failures are
     * already surfaced elsewhere (discover-tools itself, or the Test & Verify page); this endpoint
     * is purely an additive diagnostic, not a load-bearing connectivity check.
     */
    public List<Map<String, Object>> getDesignMismatches(Long id) {
        McpServerEntity entity = findOrThrow(id);
        try {
            List<McpSchema.Tool> tools = mcpClientService.discoverTools(localUrl(entity.getPort()), "none", null, null);
            return findDesignMismatches(entity.getToolDesignJson(), tools);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> testConnection(Long id) {
        McpServerEntity entity = findOrThrow(id);
        McpServerRegistry.RunningMcpServer running = registry.getOrCreate(id);
        try {
            List<McpSchema.Tool> tools = mcpClientService.discoverTools(localUrl(entity.getPort()), "none", null, null);
            running.toolCount = tools.size();
            running.status = McpServerRegistry.Status.RUNNING;
            running.lastError = null;
            Map<String, Object> out = new LinkedHashMap<>();
            // input_schema is the REAL, deployed server's own JSON schema for each tool's
            // arguments (ground truth — may differ slightly from the AI's original Step 2 design
            // if the generated script deviated) — the frontend uses it to pre-fill Step 5's test
            // args textbox instead of leaving the admin to hand-type parameter names blind.
            out.put("tools", tools.stream().map(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("name", t.name());
                tm.put("description", t.description() == null ? "" : t.description());
                tm.put("input_schema", t.inputSchema());
                return tm;
            }).collect(Collectors.toList()));
            out.put("design_mismatches", findDesignMismatches(entity.getToolDesignJson(), tools));
            return out;
        } catch (Exception e) {
            running.status = McpServerRegistry.Status.ERROR;
            running.lastError = e.getMessage();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to the deployed server: " + e.getMessage());
        }
    }

    /**
     * Compares the AI's originally approved Step-2 tool design (persisted on the entity as
     * tool_design_json — name + args per tool) against the REAL deployed server's own schema
     * (ground truth, from discoverTools). A real gap found live: a small local model can approve
     * a design listing N args for a tool but then write a generated Python function signature
     * with fewer of them — the deployed tool silently ends up with fewer real parameters than
     * intended, invisible until an admin notices missing fields much later while building an
     * action item. Surfacing the diff right here, at Test & Verify time, catches it immediately
     * instead of leaving the admin to debug "why doesn't this call have all its inputs" downstream.
     */
    private List<Map<String, Object>> findDesignMismatches(String toolDesignJson, List<McpSchema.Tool> deployedTools) {
        if (toolDesignJson == null || toolDesignJson.isBlank()) return List.of();
        List<Map<String, Object>> designedTools;
        try {
            designedTools = mapper.readValue(toolDesignJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
        Map<String, McpSchema.Tool> deployedByName = deployedTools.stream()
                .collect(Collectors.toMap(McpSchema.Tool::name, t -> t, (a, b) -> a));

        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (Map<String, Object> designed : designedTools) {
            Object nameObj = designed.get("name");
            if (!(nameObj instanceof String name) || name.isBlank()) continue;

            Set<String> designedArgNames = new LinkedHashSet<>();
            if (designed.get("args") instanceof List<?> argsList) {
                for (Object a : argsList) {
                    if (a instanceof Map<?, ?> am && am.get("name") instanceof String argName) {
                        designedArgNames.add(argName);
                    }
                }
            }

            McpSchema.Tool deployed = deployedByName.get(name);
            Map<String, Object> mismatch = new LinkedHashMap<>();
            if (deployed == null) {
                mismatch.put("tool", name);
                mismatch.put("missing_args", List.of());
                mismatch.put("not_deployed", true);
                mismatches.add(mismatch);
                continue;
            }

            // McpSchema.Tool.inputSchema() is a raw Map<String,Object> (JSON-Schema-shaped:
            // {"type":"object","properties":{...},"required":[...]}), not a typed record.
            final Set<String> deployedArgNames;
            if (deployed.inputSchema() != null && deployed.inputSchema().get("properties") instanceof Map<?, ?> props) {
                deployedArgNames = props.keySet().stream().map(String::valueOf).collect(Collectors.toSet());
            } else {
                deployedArgNames = Set.of();
            }
            List<String> missing = designedArgNames.stream().filter(a -> !deployedArgNames.contains(a)).collect(Collectors.toList());
            if (!missing.isEmpty()) {
                mismatch.put("tool", name);
                mismatch.put("missing_args", missing);
                mismatch.put("not_deployed", false);
                mismatches.add(mismatch);
            }
        }
        return mismatches;
    }

    public Map<String, Object> testTool(Long id, McpTestToolRequestDto dto) {
        if (dto.getToolName() == null || dto.getToolName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolName is required");
        }
        McpServerEntity entity = findOrThrow(id);
        McpServerRegistry.RunningMcpServer running = registry.getOrCreate(id);
        Map<String, Object> args = dto.getArgs() == null ? Map.of() : dto.getArgs();
        McpSyncClient client = mcpClientService.openClient(localUrl(entity.getPort()), "none", null, null);
        try {
            McpSchema.CallToolResult result = mcpClientService.callTool(client, dto.getToolName(), args);
            boolean isError = Boolean.TRUE.equals(result.isError());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("isError", isError);
            out.put("content", result.content());
            if (isError) {
                running.lastFailedToolCallSummary = dto.getToolName() + "(" + args + ") → " + result.content();
            }
            return out;
        } catch (Exception e) {
            running.lastFailedToolCallSummary = dto.getToolName() + "(" + args + ") → " + e.getMessage();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Tool call failed: " + e.getMessage());
        } finally {
            client.closeGracefully();
        }
    }

    public String logs(Long id) {
        findOrThrow(id);
        return registry.recentLogs(id, 500);
    }

    /** Full script+dependencies for the Settings "Edit" flow — deliberately separate from
     * {@link #toDto} / the /available endpoint, which never carry script content since that list
     * is exposed more broadly (any MANAGE_FIELDS user, for the workflow builder's server picker). */
    public Map<String, Object> getScript(Long id) {
        McpServerEntity entity = findOrThrow(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("script_content", entity.getScriptContent());
        out.put("dependencies", entity.getDependencies());
        return out;
    }

    /** The AI's revised proposal is returned for review, not auto-applied — the admin saves it via
     * {@link #update} (which redeploys) just like a first-time script, same human gate. */
    public Map<String, Object> askAiFix(Long id, McpFixRequestDto dto, Integer userId) throws Exception {
        McpServerEntity entity = findOrThrow(id);
        if (entity.getAiChatSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This server has no AI session to continue — it wasn't AI-generated.");
        }
        McpServerRegistry.RunningMcpServer running = registry.getOrCreate(id);
        String recentLogs = registry.recentLogs(id, 100);
        return aiService.fixScript(entity.getAiChatSessionId(), entity.getScriptContent(), recentLogs,
                running.lastFailedToolCallSummary, dto.getAdminDescription(), userId);
    }

    // ── EXTERNAL SERVERS (no process, MANAGE_FIELDS-gated — see McpServerController) ───────────

    /** Resolved, ready-to-use connection details for an MCP transport call — OAuth2 types are
     * resolved to a real bearer access token here (refreshing if needed), so McpClientService never
     * has to know the difference between a static token and one obtained via OAuth2. */
    public record ResolvedAuth(String serverUrl, String authType, String headerName, String token) {}

    @Transactional
    public McpServerDto createExternal(McpExternalServerCreateDto dto) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (dto.getServerUrl() == null || dto.getServerUrl().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "server_url is required");
        McpServerEntity entity = new McpServerEntity();
        entity.setServerKind("external");
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setServerUrl(dto.getServerUrl());
        entity.setEnabled(true);
        entity.setSystem(false);
        applyConnectionAuth(entity, dto.getAuth());
        entity = repo.save(entity);
        return toDto(entity);
    }

    @Transactional
    public McpServerDto updateExternal(Long id, McpExternalServerUpdateDto dto) {
        McpServerEntity entity = findExternalOrThrow(id);
        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getServerUrl() != null) entity.setServerUrl(dto.getServerUrl());
        if (dto.getAuth() != null) applyConnectionAuth(entity, dto.getAuth());
        entity = repo.save(entity);
        return toDto(entity);
    }

    /** Live discovery against the real server, using its currently-saved (and, for OAuth2,
     * freshly-resolved) auth — same underlying call McpClientService.discoverTools already provides
     * for built-in servers, just with this app's own auth resolution in front of it. */
    public Map<String, Object> testExternalConnection(Long id) {
        McpServerEntity entity = findExternalOrThrow(id);
        try {
            ResolvedAuth auth = resolveConnectionAuth(entity);
            List<McpSchema.Tool> tools = mcpClientService.discoverTools(auth.serverUrl(), auth.authType(), auth.headerName(), auth.token());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tools", tools.stream().map(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("name", t.name());
                tm.put("description", t.description() == null ? "" : t.description());
                tm.put("input_schema", t.inputSchema());
                return tm;
            }).collect(Collectors.toList()));
            return out;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to the external server: " + describeError(e));
        }
    }

    /** A real gap found live: the MCP SDK's McpSyncClient.initialize() wraps every real failure
     * (a 403 from the server, a DNS failure, a TLS error) behind one generic message —
     * "Client failed to initialize by explicit API call" — with the actually useful detail (e.g.
     * "HTTP 403 Forbidden") several levels down the exception's cause chain. Walking it here
     * instead of just calling e.getMessage() turns an unactionable error into one an admin can
     * actually do something with. */
    private String describeError(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        Set<String> seen = new LinkedHashSet<>();
        while (cur != null && seen.add(cur.getMessage() + "|" + cur.getClass().getSimpleName())) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (!sb.isEmpty()) sb.append(" — caused by: ");
                sb.append(msg);
            }
            cur = cur.getCause();
        }
        return sb.isEmpty() ? e.getClass().getSimpleName() : sb.toString();
    }

    /** Used by McpActionExecutor when a workflow item references a saved external server by id
     * (typeConfig.mcpServerId) instead of an embedded serverUrl/auth snapshot — the live resolution
     * OAuth2 needs, since a snapshot taken once at pick-time would eventually run on an expired
     * token with no way to refresh it. */
    public ResolvedAuth resolveConnectionAuth(Long serverId) {
        return resolveConnectionAuth(findExternalOrThrow(serverId));
    }

    private ResolvedAuth resolveConnectionAuth(McpServerEntity entity) {
        String type = entity.getConnectionAuthType() == null ? "none" : entity.getConnectionAuthType();
        return switch (type) {
            case "bearer" -> new ResolvedAuth(entity.getServerUrl(), "bearer", null, decryptOrNull(entity.getConnectionAuthTokenEnc()));
            case "api_key" -> new ResolvedAuth(entity.getServerUrl(), "api_key", entity.getConnectionAuthHeaderName(), decryptOrNull(entity.getConnectionAuthTokenEnc()));
            case "basic" -> {
                String user = decryptOrNull(entity.getConnectionAuthUsernameEnc());
                String pass = decryptOrNull(entity.getConnectionAuthPasswordEnc());
                String combo = (user == null ? "" : user) + ":" + (pass == null ? "" : pass);
                String b64 = Base64.getEncoder().encodeToString(combo.getBytes(StandardCharsets.UTF_8));
                yield new ResolvedAuth(entity.getServerUrl(), "basic", null, b64);
            }
            case "oauth2_client_credentials" -> new ResolvedAuth(entity.getServerUrl(), "bearer", null, resolveClientCredentialsToken(entity));
            case "oauth2_authorization_code" -> new ResolvedAuth(entity.getServerUrl(), "bearer", null, resolveAuthorizationCodeToken(entity));
            default -> new ResolvedAuth(entity.getServerUrl(), "none", null, null);
        };
    }

    /** Generalizes AzureService.getAccessTokenRaw's grant_type=client_credentials logic to an
     * admin-supplied token URL instead of the hardcoded Microsoft tenant endpoint — no user
     * interaction, no refresh_token; just re-POST with the same client_id/secret whenever the
     * cached token is near/past expiry. */
    private String resolveClientCredentialsToken(McpServerEntity entity) {
        if (entity.getOauth2AccessTokenEnc() != null && entity.getOauth2TokenExpiry() != null
                && entity.getOauth2TokenExpiry().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return decryptOrNull(entity.getOauth2AccessTokenEnc());
        }
        try {
            String clientSecret = entity.getOauth2ClientSecretEnc() != null ? aes.decrypt(entity.getOauth2ClientSecretEnc()) : "";
            StringBuilder body = new StringBuilder("grant_type=client_credentials")
                    .append("&client_id=").append(URLEncoder.encode(nullToEmpty(entity.getOauth2ClientId()), StandardCharsets.UTF_8))
                    .append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
            if (entity.getOauth2Scope() != null && !entity.getOauth2Scope().isBlank()) {
                body.append("&scope=").append(URLEncoder.encode(entity.getOauth2Scope(), StandardCharsets.UTF_8));
            }
            Map<String, Object> tokenData = postForToken(entity.getOauth2TokenUrl(), body.toString());
            storeOAuthTokens(entity, tokenData);
            return (String) tokenData.get("access_token");
        } catch (Exception e) {
            log.warn("[McpServerService] client_credentials token fetch failed for server {}: {}", entity.getId(), e.getMessage());
            return null;
        }
    }

    /** Generalizes EmailMailboxService's Gmail/Microsoft refresh-token logic to an admin-supplied
     * token URL. Returns the cached access token if still fresh, otherwise refreshes via the stored
     * refresh_token — never re-runs the interactive browser flow automatically (that only happens
     * via buildExternalAuthorizeUrl/handleExternalOAuth2Callback, admin-initiated). */
    private String resolveAuthorizationCodeToken(McpServerEntity entity) {
        if (entity.getOauth2AccessTokenEnc() == null) return null;
        if (entity.getOauth2TokenExpiry() != null && entity.getOauth2TokenExpiry().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return decryptOrNull(entity.getOauth2AccessTokenEnc());
        }
        if (entity.getOauth2RefreshTokenEnc() == null) return decryptOrNull(entity.getOauth2AccessTokenEnc());
        try {
            String refreshToken = aes.decrypt(entity.getOauth2RefreshTokenEnc());
            String clientSecret = entity.getOauth2ClientSecretEnc() != null ? aes.decrypt(entity.getOauth2ClientSecretEnc()) : "";
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(nullToEmpty(entity.getOauth2ClientId()), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
            Map<String, Object> tokenData = postForToken(entity.getOauth2TokenUrl(), body);
            storeOAuthTokens(entity, tokenData);
            return (String) tokenData.get("access_token");
        } catch (Exception e) {
            log.warn("[McpServerService] refresh_token exchange failed for server {}: {}", entity.getId(), e.getMessage());
            return decryptOrNull(entity.getOauth2AccessTokenEnc());
        }
    }

    /** Step 1 of the interactive flow — admin clicks "Authorize", frontend opens this URL in a
     * popup (mirrors EmailMailboxWizard.tsx's window.open pattern exactly). Generalizes
     * EmailMailboxService.buildGmailAuthUrl/buildMicrosoftAuthUrl to the row's own admin-supplied
     * authorize_url/client_id/scope instead of a fixed provider. */
    public String buildExternalAuthorizeUrl(Long id) {
        McpServerEntity entity = findExternalOrThrow(id);
        if (entity.getOauth2AuthorizeUrl() == null || entity.getOauth2AuthorizeUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No authorize URL configured for this server");
        }
        String sep = entity.getOauth2AuthorizeUrl().contains("?") ? "&" : "?";
        StringBuilder url = new StringBuilder(entity.getOauth2AuthorizeUrl())
                .append(sep).append("client_id=").append(URLEncoder.encode(nullToEmpty(entity.getOauth2ClientId()), StandardCharsets.UTF_8))
                .append("&redirect_uri=").append(URLEncoder.encode(oauthRedirectUri(), StandardCharsets.UTF_8))
                .append("&response_type=code")
                .append("&state=").append(id);
        if (entity.getOauth2Scope() != null && !entity.getOauth2Scope().isBlank()) {
            url.append("&scope=").append(URLEncoder.encode(entity.getOauth2Scope(), StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    /** Step 2 — the provider redirects the admin's browser back here (state = this server's id,
     * same minimal CSRF-binding EmailMailboxController's callback already relies on) with a real
     * authorization code, exchanged for tokens at the row's own token URL. */
    public void handleExternalOAuth2Callback(Long id, String code) {
        McpServerEntity entity = findExternalOrThrow(id);
        try {
            String clientSecret = entity.getOauth2ClientSecretEnc() != null ? aes.decrypt(entity.getOauth2ClientSecretEnc()) : "";
            String body = "grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(nullToEmpty(entity.getOauth2ClientId()), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(oauthRedirectUri(), StandardCharsets.UTF_8);
            Map<String, Object> tokenData = postForToken(entity.getOauth2TokenUrl(), body);
            storeOAuthTokens(entity, tokenData);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Token exchange failed: " + e.getMessage());
        }
    }

    private Map<String, Object> postForToken(String tokenUrl, String formBody) throws Exception {
        if (tokenUrl == null || tokenUrl.isBlank()) throw new IllegalStateException("No token URL configured for this server");
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody)).build();
        java.net.http.HttpResponse<String> res = java.net.http.HttpClient.newHttpClient()
                .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("Token endpoint returned " + res.statusCode() + ": " + res.body());
        }
        return mapper.readValue(res.body(), new TypeReference<>() {});
    }

    private void storeOAuthTokens(McpServerEntity entity, Map<String, Object> tokenData) {
        Object accessToken = tokenData.get("access_token");
        if (accessToken != null) entity.setOauth2AccessTokenEnc(aes.encrypt(String.valueOf(accessToken)));
        if (tokenData.get("refresh_token") != null) entity.setOauth2RefreshTokenEnc(aes.encrypt(String.valueOf(tokenData.get("refresh_token"))));
        Object expiresIn = tokenData.get("expires_in");
        if (expiresIn != null) {
            long seconds = expiresIn instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(expiresIn));
            entity.setOauth2TokenExpiry(LocalDateTime.now().plusSeconds(seconds));
        }
        repo.save(entity);
    }

    private void applyConnectionAuth(McpServerEntity entity, McpConnectionAuthDto auth) {
        if (auth == null) return;
        entity.setConnectionAuthType(auth.getType() == null || auth.getType().isBlank() ? "none" : auth.getType());
        entity.setConnectionAuthHeaderName(auth.getHeaderName());
        if (auth.getToken() != null && !auth.getToken().isBlank()) entity.setConnectionAuthTokenEnc(aes.encrypt(auth.getToken()));
        if (auth.getUsername() != null) entity.setConnectionAuthUsernameEnc(auth.getUsername().isBlank() ? null : aes.encrypt(auth.getUsername()));
        if (auth.getPassword() != null && !auth.getPassword().isBlank()) entity.setConnectionAuthPasswordEnc(aes.encrypt(auth.getPassword()));
        if (auth.getOauth2AuthorizeUrl() != null) entity.setOauth2AuthorizeUrl(auth.getOauth2AuthorizeUrl());
        if (auth.getOauth2TokenUrl() != null) entity.setOauth2TokenUrl(auth.getOauth2TokenUrl());
        if (auth.getOauth2ClientId() != null) entity.setOauth2ClientId(auth.getOauth2ClientId());
        if (auth.getOauth2ClientSecret() != null && !auth.getOauth2ClientSecret().isBlank()) entity.setOauth2ClientSecretEnc(aes.encrypt(auth.getOauth2ClientSecret()));
        if (auth.getOauth2Scope() != null) entity.setOauth2Scope(auth.getOauth2Scope());
    }

    private McpServerEntity findExternalOrThrow(Long id) {
        McpServerEntity entity = findOrThrow(id);
        if (!"external".equals(entity.getServerKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an external server");
        }
        return entity;
    }

    private String decryptOrNull(String enc) {
        if (enc == null || enc.isBlank()) return null;
        try {
            return aes.decrypt(enc);
        } catch (Exception e) {
            return null;
        }
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Same hardcoded-localhost limitation EmailMailboxService.getRedirectUri already has in this
     * codebase — no app-base-URL config exists to generalize it from. */
    private String oauthRedirectUri() {
        return "http://localhost:3000/api/v1/mcp-servers/oauth2/callback";
    }

    // ── DEPLOY ─────────────────────────────────────────────────────────────────

    private void deployAndStart(McpServerEntity entity) {
        Long id = entity.getId();
        McpServerRegistry.RunningMcpServer running = registry.getOrCreate(id);
        running.status = McpServerRegistry.Status.STARTING;
        running.lastError = null;
        stopProcessOnly(running);

        try {
            Path serverDir = resolveServerDir(id);
            Files.createDirectories(serverDir);
            Path scriptFile = serverDir.resolve("server.py");
            Files.writeString(scriptFile, entity.getScriptContent() == null ? "" : entity.getScriptContent(), StandardCharsets.UTF_8);

            Path venvDir = serverDir.resolve("venv");
            Path venvPython = resolveVenvPython(venvDir);
            if (!Files.exists(venvPython)) {
                runAndWait(List.of(pythonExecutable, "-m", "venv", venvDir.toString()), serverDir, 60, "creating virtual environment");
                venvPython = resolveVenvPython(venvDir);
            }

            List<String> deps = parseDependencies(entity.getDependencies());
            if (!deps.isEmpty()) {
                List<String> pipCmd = new ArrayList<>(List.of(venvPython.toString(), "-m", "pip", "install",
                        "--disable-pip-version-check", "-q"));
                pipCmd.addAll(deps);
                runAndWait(pipCmd, serverDir, pipInstallTimeoutSeconds, "installing dependencies (" + String.join(", ", deps) + ")");
            }

            ProcessBuilder pb = new ProcessBuilder(venvPython.toString(), scriptFile.toString());
            pb.directory(serverDir.toFile());
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.put("TARGET_API_BASE_URL", entity.getTargetApiBaseUrl() == null ? "" : entity.getTargetApiBaseUrl());
            env.put("TARGET_API_CREDENTIAL", decryptCredentialOrEmpty(entity.getTargetApiAuth()));
            env.put("MCP_SERVER_PORT", String.valueOf(entity.getPort()));

            Process process = pb.start();
            running.process = process;
            startLogDrainThread(id, process);

            Thread.sleep(startupGraceMs);
            if (!process.isAlive()) {
                running.status = McpServerRegistry.Status.ERROR;
                running.lastError = "Process exited immediately (code " + process.exitValue() + ") — see logs.";
                log.warn("[McpServerService] server {} exited immediately with code {}", id, process.exitValue());
                return;
            }

            try {
                List<McpSchema.Tool> tools = mcpClientService.discoverTools(localUrl(entity.getPort()), "none", null, null);
                running.toolCount = tools.size();
                running.status = McpServerRegistry.Status.RUNNING;
            } catch (Exception e) {
                running.status = McpServerRegistry.Status.ERROR;
                running.lastError = "Deployed but not responding to MCP requests: " + e.getMessage();
                log.warn("[McpServerService] server {} deployed but discoverTools failed: {}", id, e.getMessage());
            }
        } catch (Exception e) {
            running.status = McpServerRegistry.Status.ERROR;
            running.lastError = e.getMessage();
            log.error("[McpServerService] deployAndStart failed for server {}: {}", id, e.getMessage(), e);
        }
    }

    // ── HELPERS ────────────────────────────────────────────────────────────────

    private Path resolveServerDir(Long id) {
        Path dir = scriptsRoot.resolve(String.valueOf(id)).normalize();
        if (!dir.startsWith(scriptsRoot)) throw new SecurityException("Path traversal attempt detected");
        return dir;
    }

    private Path resolveVenvPython(Path venvDir) {
        Path unix = venvDir.resolve("bin").resolve("python");
        if (Files.exists(unix)) return unix;
        Path windows = venvDir.resolve("Scripts").resolve("python.exe");
        if (Files.exists(windows)) return windows;
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? windows : unix;
    }

    private void runAndWait(List<String> command, Path workDir, int timeoutSeconds, String stepDescription) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("Timed out " + stepDescription + " (" + timeoutSeconds + "s)");
        }
        if (p.exitValue() != 0) {
            String tail = output.length() > 1500 ? output.substring(output.length() - 1500) : output.toString();
            throw new IOException("Failed " + stepDescription + ": " + tail);
        }
    }

    private List<String> parseDependencies(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[\\r\\n,]+")).map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
    }

    private String decryptCredentialOrEmpty(String targetApiAuthJson) {
        if (targetApiAuthJson == null || targetApiAuthJson.isBlank()) return "";
        try {
            Map<String, Object> auth = mapper.readValue(targetApiAuthJson, new TypeReference<>() {});
            Object tokenEnc = auth.get("tokenEnc");
            if (tokenEnc == null) return "";
            return aes.decrypt(String.valueOf(tokenEnc));
        } catch (Exception e) {
            log.warn("[McpServerService] failed to decrypt target API credential: {}", e.getMessage());
            return "";
        }
    }

    private String localUrl(Integer port) {
        return "http://localhost:" + port;
    }

    private void startLogDrainThread(Long id, Process process) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    registry.appendLog(id, line);
                }
            } catch (IOException ignored) {
                // stream closes when the process exits/is destroyed — expected, not an error
            }
        }, "mcp-server-" + id + "-log-drain");
        t.setDaemon(true);
        t.start();
    }

    private void stopProcessOnly(McpServerRegistry.RunningMcpServer running) {
        if (running.process != null && running.process.isAlive()) {
            running.process.destroy();
            try {
                if (!running.process.waitFor(5, TimeUnit.SECONDS)) running.process.destroyForcibly();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                running.process.destroyForcibly();
            }
        }
        running.process = null;
    }

    private void stopInternal(Long id) {
        McpServerRegistry.RunningMcpServer running = registry.get(id);
        if (running == null) return;
        stopProcessOnly(running);
        running.status = McpServerRegistry.Status.STOPPED;
    }

    private void deleteScriptDir(Long id) {
        try {
            Path dir = resolveServerDir(id);
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
                }
            }
        } catch (IOException e) {
            log.warn("[McpServerService] failed to delete script dir for server {}: {}", id, e.getMessage());
        }
    }

    private String serializeAuth(McpTargetAuthDto auth) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", auth == null || auth.getType() == null || auth.getType().isBlank() ? "none" : auth.getType());
        map.put("location", auth == null || auth.getLocation() == null || auth.getLocation().isBlank() ? "header" : auth.getLocation());
        map.put("name", auth == null || auth.getName() == null ? "" : auth.getName());
        String secret = auth == null ? null : auth.getSecretValue();
        map.put("tokenEnc", (secret == null || secret.isBlank()) ? null : aes.encrypt(secret));
        try {
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize auth config");
        }
    }

    private McpServerDto toDto(McpServerEntity e) {
        McpServerDto dto = new McpServerDto();
        dto.setId(e.getId());
        dto.setServerKind(e.getServerKind());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setTargetApiBaseUrl(e.getTargetApiBaseUrl());
        dto.setPort(e.getPort());
        dto.setEnabled(e.isEnabled());
        dto.setSystem(e.isSystem());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setServerUrl(e.getServerUrl());
        dto.setConnectionAuthType(e.getConnectionAuthType());
        dto.setConnectionAuthHeaderName(e.getConnectionAuthHeaderName());
        dto.setOauth2ClientId(e.getOauth2ClientId());
        dto.setOauth2AuthorizeUrl(e.getOauth2AuthorizeUrl());
        dto.setOauth2TokenUrl(e.getOauth2TokenUrl());
        dto.setOauth2Scope(e.getOauth2Scope());
        dto.setOauth2Authorized(e.getOauth2AccessTokenEnc() != null);
        if ("external".equals(e.getServerKind())) {
            dto.setStatus("EXTERNAL");
        } else {
            McpServerRegistry.RunningMcpServer running = registry.get(e.getId());
            dto.setStatus(running == null ? McpServerRegistry.Status.STOPPED.name() : running.status.name());
            dto.setToolCount(running == null ? null : running.toolCount);
            dto.setLastError(running == null ? null : running.lastError);
        }
        return dto;
    }

    private McpServerEntity findOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP server not found"));
    }

    private void validateCreate(McpServerCreateDto dto) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (dto.getTargetApiBaseUrl() == null || dto.getTargetApiBaseUrl().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetApiBaseUrl is required");
        if (dto.getScriptContent() == null || dto.getScriptContent().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scriptContent is required");
    }

    /** Used by McpServerBootstrapper on boot — every enabled GENERATED row, redeployed fresh.
     * External rows have no process to (re)deploy. */
    List<McpServerEntity> findAllEnabled() {
        return repo.findByEnabledTrue().stream().filter(e -> "generated".equals(e.getServerKind())).collect(Collectors.toList());
    }

    void deployAndStartOnBoot(McpServerEntity entity) {
        deployAndStart(entity);
    }
}
