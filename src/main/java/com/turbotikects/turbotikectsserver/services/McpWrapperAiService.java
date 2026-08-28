package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.dto.mcp.McpAuthShapeDto;
import com.turbotikects.turbotikectsserver.dto.mcp.McpDesignRequestDto;
import com.turbotikects.turbotikectsserver.dto.mcp.McpGenerateScriptRequestDto;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FEAT-19 — AI-Generated MCP Servers. Three structured-JSON LLM calls, each following
 * {@link TemplateService#aiMapCallFields}'s exact call/parse/error-handling shape: get the active
 * AI, build a system+user {@link LlmStructure} prompt demanding a specific JSON shape, call
 * {@link AiSettingsService#sendLlmRequest} in JSON mode, strip markdown fences, parse-or-400.
 * <p>
 * Split into three focused calls rather than one "read docs, design tools, and write the whole
 * script" mega-prompt — the same lesson this codebase already learned the hard way splitting
 * {@code aiRefineResponseMapping} into {@code aiExtractResponseCaptures}/{@code aiMapResponseCaptures}
 * (see PROGRESS.md Phase 12): a single overloaded prompt is unreliable for a small local model.
 * <p>
 * The generated script never receives the target API's actual credential in its source text — it
 * reads it from the {@code TARGET_API_CREDENTIAL} environment variable at runtime (injected by
 * {@link McpServerService} when it spawns the process), and the base URL from
 * {@code TARGET_API_BASE_URL}, and its listen port from {@code MCP_SERVER_PORT}. This means the
 * secret never needs to reach the LLM at all — {@link #generateScript} only takes the auth
 * <em>shape</em> (type/location/name), never the value.
 */
@Slf4j
@Service
public class McpWrapperAiService {

    private final AiSettingsService aiSettingsService;
    private final AiChatService aiChatService;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpWrapperAiService(AiSettingsService aiSettingsService, AiChatService aiChatService) {
        this.aiSettingsService = aiSettingsService;
        this.aiChatService = aiChatService;
    }

    // ── STEP 2 — PROPOSE TOOL DESIGN ────────────────────────────────────────────

    public Map<String, Object> proposeDesign(McpDesignRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getBaseUrl() == null || dto.getBaseUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl is required");
        }
        if (dto.getDocs() == null || dto.getDocs().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "docs is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            sessionId = aiChatService.createSession(userId, "mcp_wrapper_builder", null).getId();
        }
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                Given a REST API's base URL and its documentation (freeform text and/or an OpenAPI/
                Swagger spec), propose a small set of MCP tools that wrap the most useful endpoints.
                Produce a JSON object with EXACTLY this shape (no markdown, no explanation, ONLY the
                JSON):
                {
                  "tools": [
                    {
                      "name": "snake_case_tool_name",
                      "description": "one sentence, what this tool does",
                      "method": "GET"|"POST"|"PUT"|"PATCH"|"DELETE",
                      "path": "/v1/relative/path/{withParams}",
                      "args": [
                        {"name": "argName", "type": "string"|"number"|"boolean", "required": true|false,
                         "location": "path"|"query"|"body", "description": "..."}
                      ],
                      "output": {
                        "description": "one sentence, what this tool's response represents",
                        "fields": [
                          {"name": "responseFieldName", "type": "string"|"number"|"boolean"|"array"|"object",
                           "description": "..."}
                        ]
                      }
                    }
                  ]
                }
                Rules:
                - Only propose tools for endpoints actually described in the documentation — never
                  invent an endpoint that isn't there.
                - Prefer the endpoints an agent building an automated workflow would actually need
                  (create/read/update the primary resources), not every endpoint in the docs.
                - "path" is relative to the given base URL and must use the API's own path-parameter
                  syntax exactly as documented (e.g. "{id}").
                - "args" MUST cover every parameter the endpoint actually needs to be called
                  successfully, with "required" set accurately per the documentation — never mark a
                  parameter required=false just because it's easier to omit, and never invent a
                  parameter the docs don't mention.
                - "output.fields" lists the top-level fields of what the endpoint actually returns per
                  the documentation (or, if the docs don't show a sample response, your best-informed
                  guess from the endpoint's purpose) — keep it to the fields a caller would actually
                  use, not an exhaustive dump of every nested field.
                - Keep the list focused — typically 3 to 8 tools, not an exhaustive wrapper of the
                  entire API surface.
                - If earlier turns are present in this conversation (a re-run after refined intent),
                  keep prior tools still valid and only change what the newest input implies should
                  change.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("API base URL: " + dto.getBaseUrl() + "\n\nAPI documentation:\n" + dto.getDocs());

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(history);
        llmRequest.add(user);

        String raw = aiSettingsService.sendLlmRequest(aiSettings, llmRequest);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> draft;
        try {
            draft = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[McpWrapperAiService] proposeDesign: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a different AI provider/model.");
        }

        if (!(draft.get("tools") instanceof List<?> tools) || tools.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not propose any tools — try adding more detail to the documentation.");
        }

        aiChatService.appendMessage(sessionId, "user", user.getContent());
        aiChatService.appendMessage(sessionId, "assistant", cleaned);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tools", draft.get("tools"));
        out.put("sessionId", sessionId);
        return out;
    }

    // ── STEP 3 — GENERATE SCRIPT ────────────────────────────────────────────────

    public Map<String, Object> generateScript(McpGenerateScriptRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getSessionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required — call proposeDesign first");
        }
        if (dto.getApprovedTools() == null || dto.getApprovedTools().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approvedTools is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        String toolsJson;
        try {
            toolsJson = mapper.writeValueAsString(dto.getApprovedTools());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approvedTools payload");
        }

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                Write a complete, runnable Python MCP server script implementing EXACTLY the given
                approved tool list — no more, no fewer. Produce a JSON object with EXACTLY this shape
                (no markdown, no explanation, ONLY the JSON):
                {
                  "script": "the full Python source, as a single string with real \\n newlines",
                  "dependencies": ["pip package names the script imports beyond the standard library"]
                }
                Rules:
                - Use EXACTLY this server API shape (verified against the installed `mcp` 2.0.0
                  package — do not use `FastMCP`, which does not exist in this version):
                      import os
                      from mcp.server.mcpserver import MCPServer
                      server = MCPServer("server-name")
                      @server.tool()
                      def some_tool(arg: str) -> str:
                          \\"\\"\\"One-line description.\\"\\"\\"
                          ...
                      if __name__ == "__main__":
                          port = int(os.environ.get("MCP_SERVER_PORT", "8000"))
                          server.run(transport="streamable-http", host="0.0.0.0", port=port)
                  Always list "mcp" in "dependencies" — it is never pre-installed.
                - The script MUST read three values from the process environment, never hardcode
                  them: TARGET_API_BASE_URL (the API's base URL), TARGET_API_CREDENTIAL (the auth
                  secret — absent/empty when auth type is "none"), and MCP_SERVER_PORT (the port to
                  listen on, read exactly as shown above). Never invent a different variable name.
                - Place the credential exactly per the given auth shape: a "header" location means an
                  HTTP header of the given name; "query" means a URL query parameter of the given
                  name; "body" means a JSON field of the given name in the request body. When auth
                  type is "none", make no attempt to send any credential.
                - List "requests" in "dependencies" only if the script actually imports it — don't
                  assume anything beyond the Python standard library and "mcp" is available without
                  listing it.
                - Every tool's function signature must match its documented args (name/type/required)
                  exactly, and must return the target API's real response, not a stub or placeholder.
                  Use each tool's "output" (from the approved design) to write an accurate return-type
                  annotation and docstring describing what the caller gets back.
                - If earlier turns are present in this conversation, this is a revision — keep the
                  overall structure consistent with what was discussed, only changing what the newest
                  input implies should change.
                """);

        McpAuthShapeDto auth = dto.getAuth() != null ? dto.getAuth() : new McpAuthShapeDto();
        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Approved tools:\n" + toolsJson +
                "\n\nTarget API auth — type: " + nullToNone(auth.getType()) +
                ", location: " + nullToNone(auth.getLocation()) +
                ", name: " + nullToNone(auth.getName()));

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(history);
        llmRequest.add(user);

        Map<String, Object> out = callAndParseScriptResponse(aiSettings, llmRequest, sessionId, user.getContent());
        out.put("sessionId", sessionId);
        return out;
    }

    // ── FIX LOOP ─────────────────────────────────────────────────────────────────

    /** Continuation call on the server's existing session. recentLogs/failedToolCallSummary are
     * fetched server-side by the caller (McpServerService, from McpServerRegistry) — never typed
     * by the admin. */
    public Map<String, Object> fixScript(Long sessionId, String currentScript, String recentLogs,
                                          String failedToolCallSummary, String adminDescription, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You previously generated a Python MCP server script and it has a real problem. Given
                the current script, the server's recent process output, and optionally a specific
                failed tool call and the admin's description of what should have happened, return a
                corrected script. Produce a JSON object with EXACTLY this shape (no markdown, no
                explanation, ONLY the JSON):
                {
                  "script": "the full corrected Python source, as a single string with real \\n newlines",
                  "dependencies": ["pip package names the corrected script imports"]
                }
                Rules:
                - Fix the actual root cause visible in the logs/failed call — don't just paper over
                  the symptom.
                - Keep every rule from the original generation prompt still in force: read
                  TARGET_API_BASE_URL / TARGET_API_CREDENTIAL / MCP_SERVER_PORT from the environment,
                  never hardcode them.
                - Preserve all tools/behavior that weren't part of the problem.
                """);

        StringBuilder userContent = new StringBuilder();
        userContent.append("Current script:\n").append(currentScript);
        if (recentLogs != null && !recentLogs.isBlank()) {
            userContent.append("\n\nRecent process output (stdout/stderr):\n").append(recentLogs);
        }
        if (failedToolCallSummary != null && !failedToolCallSummary.isBlank()) {
            userContent.append("\n\nLast failed tool call:\n").append(failedToolCallSummary);
        }
        if (adminDescription != null && !adminDescription.isBlank()) {
            userContent.append("\n\nAdmin's description of what should have happened:\n").append(adminDescription);
        }

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent(userContent.toString());

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(history);
        llmRequest.add(user);

        Map<String, Object> out = callAndParseScriptResponse(aiSettings, llmRequest, sessionId, user.getContent());
        out.put("sessionId", sessionId);
        return out;
    }

    // ── SHARED: call + parse a {"script","dependencies"} response ────────────────

    private Map<String, Object> callAndParseScriptResponse(AiSettingsEntity aiSettings, List<LlmStructure> llmRequest,
                                                             Long sessionId, String userContentForHistory)
            throws IOException, URISyntaxException, InterruptedException {
        String raw = aiSettingsService.sendLlmRequest(aiSettings, llmRequest);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> draft;
        try {
            draft = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[McpWrapperAiService] script generation: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a different AI provider/model.");
        }

        Object script = draft.get("script");
        if (!(script instanceof String s) || s.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The AI did not return a script.");
        }
        List<?> dependencies = draft.get("dependencies") instanceof List<?> l ? l : List.of();

        aiChatService.appendMessage(sessionId, "user", userContentForHistory);
        aiChatService.appendMessage(sessionId, "assistant", cleaned);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("script", s);
        out.put("dependencies", dependencies);
        return out;
    }

    private static String nullToNone(String s) {
        return (s == null || s.isBlank()) ? "none" : s;
    }
}
