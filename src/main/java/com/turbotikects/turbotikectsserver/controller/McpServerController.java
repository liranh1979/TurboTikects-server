package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.mcp.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.McpServerService;
import com.turbotikects.turbotikectsserver.services.McpWrapperAiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * FEAT-19 — AI-Generated MCP Servers. Gated by MANAGE_MCP_SERVERS at the class level, exactly like
 * every other settings surface — PLUS an explicit is_super_admin check in every method, since this
 * feature spawns real OS processes and a permission grant alone isn't a strong enough boundary for
 * that (mirrors SystemSettingsController/SslSettingsController's requireSuperAdmin pattern).
 */
@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequirePermission("MANAGE_MCP_SERVERS")
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpWrapperAiService mcpWrapperAiService;

    public McpServerController(McpServerService mcpServerService, McpWrapperAiService mcpWrapperAiService) {
        this.mcpServerService = mcpServerService;
        this.mcpWrapperAiService = mcpWrapperAiService;
    }

    @GetMapping
    public List<McpServerDto> list(HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.list();
    }

    /** Read-only, lighter-weight listing for the Action Item builder's "Built-in Server" picker —
     * available to anyone who can already build workflows (MANAGE_FIELDS, same gate
     * McpController's discover-tools already uses), not just super-admins. Management
     * (create/edit/delete/deploy) stays super-admin-only above; McpServerDto never carries the
     * script/docs/credential, so it's safe to expose more broadly. Method-level annotation
     * overrides the class-level MANAGE_MCP_SERVERS requirement (see PermissionInterceptor). */
    @RequirePermission("MANAGE_FIELDS")
    @GetMapping("/available")
    public List<McpServerDto> listAvailable() {
        return mcpServerService.list();
    }

    /** Same relaxed gate as /available, for the same reason — see McpServerService.
     * getDesignMismatches' javadoc. */
    @RequirePermission("MANAGE_FIELDS")
    @GetMapping("/{id}/design-mismatches")
    public List<Map<String, Object>> designMismatches(@PathVariable Long id) {
        return mcpServerService.getDesignMismatches(id);
    }

    @GetMapping("/{id}")
    public McpServerDto get(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.get(id);
    }

    @PostMapping
    public McpServerDto create(@RequestBody McpServerCreateDto dto, HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.create(dto, currentUserId(request));
    }

    @PatchMapping("/{id}")
    public McpServerDto update(@PathVariable Long id, @RequestBody McpServerUpdateDto dto, HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        mcpServerService.delete(id);
    }

    @PostMapping("/{id}/start")
    public void start(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        mcpServerService.start(id);
    }

    @PostMapping("/{id}/stop")
    public void stop(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        mcpServerService.stop(id);
    }

    @PostMapping("/{id}/restart")
    public void restart(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        mcpServerService.restart(id);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.testConnection(id);
    }

    @PostMapping("/{id}/test-tool")
    public Map<String, Object> testTool(@PathVariable Long id, @RequestBody McpTestToolRequestDto dto, HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.testTool(id, dto);
    }

    @GetMapping("/{id}/logs")
    public Map<String, String> logs(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        return Map.of("logs", mcpServerService.logs(id));
    }

    @GetMapping("/{id}/script")
    public Map<String, Object> getScript(@PathVariable Long id, HttpServletRequest request) {
        requireSuperAdmin(request);
        return mcpServerService.getScript(id);
    }

    @PostMapping("/ai-design")
    public Map<String, Object> aiDesign(@RequestBody McpDesignRequestDto dto, HttpServletRequest request)
            throws IOException, URISyntaxException, InterruptedException {
        requireSuperAdmin(request);
        return mcpWrapperAiService.proposeDesign(dto, currentUserId(request));
    }

    @PostMapping("/ai-generate")
    public Map<String, Object> aiGenerate(@RequestBody McpGenerateScriptRequestDto dto, HttpServletRequest request)
            throws IOException, URISyntaxException, InterruptedException {
        requireSuperAdmin(request);
        return mcpWrapperAiService.generateScript(dto, currentUserId(request));
    }

    @PostMapping("/{id}/ai-fix")
    public Map<String, Object> aiFix(@PathVariable Long id, @RequestBody McpFixRequestDto dto, HttpServletRequest request) throws Exception {
        requireSuperAdmin(request);
        return mcpServerService.askAiFix(id, dto, currentUserId(request));
    }

    // ── EXTERNAL SERVERS ─────────────────────────────────────────────────────
    // Deliberately MANAGE_FIELDS-only, no super-admin check — an external row never spawns an OS
    // process (unlike everything above), so it's the same trust level as building the
    // templates/workflows that will call it: anyone who can build a template can register the
    // external MCP server that template's mcp_tool action items need. See V135's migration note.

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/external")
    public McpServerDto createExternal(@RequestBody McpExternalServerCreateDto dto) {
        return mcpServerService.createExternal(dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PatchMapping("/external/{id}")
    public McpServerDto updateExternal(@PathVariable Long id, @RequestBody McpExternalServerUpdateDto dto) {
        return mcpServerService.updateExternal(id, dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @DeleteMapping("/external/{id}")
    public void deleteExternal(@PathVariable Long id) {
        mcpServerService.delete(id);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/external/{id}/test")
    public Map<String, Object> testExternal(@PathVariable Long id) {
        return mcpServerService.testExternalConnection(id);
    }

    @RequirePermission("MANAGE_FIELDS")
    @GetMapping("/external/{id}/oauth2/authorize")
    public Map<String, String> externalOauthAuthorize(@PathVariable Long id) {
        return Map.of("authUrl", mcpServerService.buildExternalAuthorizeUrl(id));
    }

    /** Hit by the OAuth provider's browser redirect (the admin's own browser, same authenticated
     * session that opened the popup) — MANAGE_FIELDS-gated like the rest of this section, not a
     * public endpoint. state = the server id, same minimal binding EmailMailboxController's
     * equivalent callback already uses. */
    @RequirePermission("MANAGE_FIELDS")
    @GetMapping("/oauth2/callback")
    public String externalOauthCallback(@RequestParam String code, @RequestParam String state) {
        mcpServerService.handleExternalOAuth2Callback(Long.parseLong(state), code);
        return "<html><body><script>window.close();</script><p>Authorized. You can close this window.</p></body></html>";
    }

    private void requireSuperAdmin(HttpServletRequest req) {
        UserDto caller = (UserDto) req.getAttribute("currentUser");
        if (caller == null || !caller.isSuperAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin only");
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
