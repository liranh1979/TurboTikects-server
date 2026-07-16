package com.turbotikects.turbotikectsserver.services;

import com.jayway.jsonpath.JsonPath;
import com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.WorkflowItemRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Executes 'mcp_tool' workflow items (FEAT-06 Phase 6) — parallel to Phase 4's
 * ExternalApiActionExecutor (ordered call sequence, auto-runs on activation, declarative JSON
 * config interpreted here rather than any generated/eval'd code), but calling structured MCP tools
 * via McpClientService instead of raw HTTP. One MCP connection is opened for the whole item's call
 * sequence (not one per call) — an item's calls are all against the SAME configured serverUrl, and
 * MCP's initialize-handshake-per-connection model makes reconnecting per call wasteful.
 *
 * typeConfig shape:
 * {@code
 * { "serverUrl": "https://...", "auth": {"type":"none"|"bearer", "tokenEnc" (server-managed)},
 *   "calls": [ { "id", "order", "toolName",
 *                "argumentMappings": [{"toolArgument","ticketField"} | {"toolArgument","captureName"}],
 *                "responseCaptures": [{"name","resultPath"}] } ],
 *   "fieldMappings": { "response": [{"captureName","target"}] } }
 * }
 *
 * Simpler than external_api's shape in one respect: MCP tool arguments are a structured
 * Map&lt;String,Object&gt;, not a string template, so each argumentMapping declares its source
 * directly (a ticket field, or an earlier call's captured value) rather than going through a
 * {{placeholder}} indirection layer. A "ticketField" value prefixed "this." reads from the item's
 * own field_values instead of the ticket (same grammar as ExternalApiActionExecutor). "resultPath"
 * is a JSONPath expression evaluated against
 * whichever of the tool result's structuredContent / first TextContent-parsed-as-JSON / a
 * {"text": ...} fallback is available — same JSONPath library and captured-value-accumulation
 * model as external_api, for consistency between the two action types.
 */
@Slf4j
@Service
public class McpActionExecutor {

    private static final int MAX_CALLS = 10;

    private final WorkflowItemRepository itemRepo;
    private final TicketRepository ticketRepo;
    private final AesEncryptionUtils aes;
    private final AdminInboxService adminInboxService;
    private final McpClientService mcpClientService;
    private final WorkflowService workflowService;

    public McpActionExecutor(WorkflowItemRepository itemRepo, TicketRepository ticketRepo,
                              AesEncryptionUtils aes, AdminInboxService adminInboxService,
                              McpClientService mcpClientService,
                              @Lazy WorkflowService workflowService) {
        this.itemRepo = itemRepo;
        this.ticketRepo = ticketRepo;
        this.aes = aes;
        this.adminInboxService = adminInboxService;
        this.mcpClientService = mcpClientService;
        this.workflowService = workflowService;
    }

    public void execute(WorkflowItemEntity item) {
        Long itemId = item.getId();
        new Thread(() -> runSequence(itemId), "mcp-action-exec-" + itemId).start();
    }

    /**
     * FEAT-06 Phase 7 — "test this call now". Mirrors ExternalApiActionExecutor.testRun exactly:
     * reuses the real runCall unchanged against a throwaway in-memory ticket, no persistence.
     * bearerToken is passed in explicitly (rather than decrypted from typeConfig here) because the
     * Designer's in-memory draft may hold either a freshly-typed plaintext token (not yet saved/
     * encrypted) or nothing at all if testing an unmodified already-saved node — the caller
     * (controller) resolves which one applies before calling this.
     */
    public WorkflowActionTestResult testRun(Map<String, Object> typeConfig, String bearerToken, Map<String, String> sampleTicketFields) {
        List<Map<String, Object>> trace = new ArrayList<>();
        McpSyncClient client = null;
        try {
            String serverUrl = typeConfig != null ? str(typeConfig.get("serverUrl")) : null;
            if (serverUrl == null || serverUrl.isBlank()) return WorkflowActionTestResult.failure("No MCP server URL configured", trace);

            List<Map<String, Object>> calls = new ArrayList<>(listOf(typeConfig.get("calls")));
            if (calls.isEmpty()) return WorkflowActionTestResult.failure("No tool calls configured", trace);
            if (calls.size() > MAX_CALLS) return WorkflowActionTestResult.failure("Too many calls configured", trace);
            calls.sort(Comparator.comparingInt(c -> c.get("order") != null ? ((Number) c.get("order")).intValue() : 0));

            TicketEntity syntheticTicket = new TicketEntity();
            syntheticTicket.setTitle(sampleTicketFields.getOrDefault("title", ""));
            syntheticTicket.setDescription(sampleTicketFields.get("description"));
            syntheticTicket.setStatus(sampleTicketFields.getOrDefault("status", "new"));
            syntheticTicket.setPriority(sampleTicketFields.getOrDefault("priority", "medium"));
            syntheticTicket.setTicketData(new LinkedHashMap<>(sampleTicketFields));

            client = mcpClientService.openClient(serverUrl, bearerToken);
            Map<String, Object> vars = new LinkedHashMap<>();
            for (Map<String, Object> call : calls) {
                // No WorkflowItemEntity exists in test mode — a "this." source always reads as empty.
                runCall(client, call, syntheticTicket, null, vars, trace);
            }
            return WorkflowActionTestResult.success(vars, trace);
        } catch (Exception e) {
            return WorkflowActionTestResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), trace);
        } finally {
            if (client != null) client.closeGracefully();
        }
    }

    private void runSequence(Long itemId) {
        WorkflowItemEntity item = itemRepo.findById(itemId).orElse(null);
        if (item == null) return;
        McpSyncClient client = null;
        try {
            Map<String, Object> typeConfig = item.getTypeConfig();
            String serverUrl = typeConfig != null ? str(typeConfig.get("serverUrl")) : null;
            if (serverUrl == null || serverUrl.isBlank()) {
                fail(item, "No MCP server URL configured on this action item");
                return;
            }

            List<Map<String, Object>> calls = new ArrayList<>(listOf(typeConfig.get("calls")));
            if (calls.isEmpty()) {
                fail(item, "No tool calls configured on this action item");
                return;
            }
            if (calls.size() > MAX_CALLS) {
                fail(item, "Too many calls configured (" + calls.size() + " > " + MAX_CALLS + ")");
                return;
            }
            calls.sort(Comparator.comparingInt(c -> c.get("order") != null ? ((Number) c.get("order")).intValue() : 0));

            TicketEntity ticket = ticketRepo.findById(item.getTicketId()).orElse(null);
            if (ticket == null) {
                fail(item, "Ticket not found");
                return;
            }

            String bearerToken = decryptOrNull(authTokenOf(typeConfig));
            client = mcpClientService.openClient(serverUrl, bearerToken);

            Map<String, Object> vars = new LinkedHashMap<>();
            for (Map<String, Object> call : calls) {
                runCall(client, call, ticket, item, vars, null);
            }

            boolean ticketChanged = false;
            Map<String, Object> fieldMappings = asMap(typeConfig.get("fieldMappings"));
            for (Map<String, Object> respMap : listOf(fieldMappings.get("response"))) {
                String captureName = str(respMap.get("captureName"));
                String target = str(respMap.get("target"));
                if (captureName == null || target == null || !vars.containsKey(captureName)) continue;
                if (applyTarget(item, ticket, target, vars.get(captureName))) ticketChanged = true;
            }
            if (ticketChanged) ticketRepo.save(ticket);

            item.setStatus("done");
            item.setLastError(null);
            itemRepo.save(item);
            log.info("[Mcp] Item {} completed successfully ({} calls)", itemId, calls.size());
            workflowService.onItemCompleted(itemId, item.getTicketId());
        } catch (Exception e) {
            log.error("[Mcp] Item {} failed: {}", itemId, e.getMessage(), e);
            WorkflowItemEntity fresh = itemRepo.findById(itemId).orElse(null);
            if (fresh != null) fail(fresh, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            if (client != null) client.closeGracefully();
        }
    }

    /** trace is non-null only in {@link #testRun} — appended to for the "test this call now" result panel, ignored (null) during real item execution. */
    private void runCall(McpSyncClient client, Map<String, Object> call, TicketEntity ticket, WorkflowItemEntity item, Map<String, Object> vars, List<Map<String, Object>> trace) {
        String toolName = str(call.get("toolName"));
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("A call has no toolName configured");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        for (Map<String, Object> mapping : listOf(call.get("argumentMappings"))) {
            String toolArgument = str(mapping.get("toolArgument"));
            if (toolArgument == null) continue;
            String ticketField = str(mapping.get("ticketField"));
            String captureName = str(mapping.get("captureName"));
            if (captureName != null) {
                // A missing capture (never extracted, or extracted from an earlier call that
                // itself failed) must not silently become a null argument — that produces a
                // confusing MCP schema-validation error far from its real cause. Fail loudly and
                // specifically here instead.
                if (!vars.containsKey(captureName)) {
                    throw new IllegalArgumentException("Argument '" + toolArgument + "' on tool '" + toolName +
                            "' references captureName '" + captureName + "', which was never captured by an earlier call");
                }
                arguments.put(toolArgument, vars.get(captureName));
            } else if (ticketField != null) {
                arguments.put(toolArgument, resolveInputField(ticket, item, ticketField));
            }
        }

        log.info("[Mcp] Calling tool '{}' with arguments {}", toolName, arguments.keySet());
        McpSchema.CallToolResult result = mcpClientService.callTool(client, toolName, arguments);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new RuntimeException("Tool '" + toolName + "' reported an error: " + extractText(result));
        }

        Object resultObject = buildResultObject(result);
        for (Map<String, Object> cap : listOf(call.get("responseCaptures"))) {
            String name = str(cap.get("name"));
            String resultPath = str(cap.get("resultPath"));
            if (name == null || resultPath == null) continue;
            try {
                Object extracted = JsonPath.read(resultObject, resultPath);
                vars.put(name, extracted);
            } catch (Exception e) {
                log.warn("[Mcp] Capture '{}' (resultPath {}) failed on tool '{}': {}", name, resultPath, toolName, e.getMessage());
            }
        }

        if (trace != null) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", toolName);
            entry.put("request", "tool:" + toolName + " args=" + arguments.keySet());
            entry.put("status", Boolean.TRUE.equals(result.isError()) ? "error" : "ok");
            entry.put("responsePreview", extractText(result));
            trace.add(entry);
        }
    }

    /** Assembles a JSONPath-queryable object from a CallToolResult: prefers structuredContent, falls back to parsing the first TextContent as JSON, falls back to {"text": <concatenated text>} so $.text always works as an escape hatch. */
    @SuppressWarnings("unchecked")
    private Object buildResultObject(McpSchema.CallToolResult result) {
        if (result.structuredContent() != null) return result.structuredContent();
        String text = extractText(result);
        if (text != null && !text.isBlank()) {
            try {
                Object parsed = JsonPath.parse(text).json();
                // Plain-text tool output (e.g. echo's "hello world") is NOT valid JSON, but
                // JsonPath's parser can still "succeed" and hand back a bare scalar rather than
                // throwing — a bare scalar isn't queryable by a JSONPath like "$.text" (it has no
                // properties), which silently no-ops every capture against it. Only trust the parse
                // if it actually produced a JSON object/array; otherwise fall through to the
                // text-wrapper below, which the near-universal "$.text" capture path expects.
                if (parsed instanceof Map || parsed instanceof List) return parsed;
            } catch (Exception ignored) {
                // not JSON — fall through to the text-wrapper fallback
            }
        }
        return Map.of("text", text != null ? text : "");
    }

    private String extractText(McpSchema.CallToolResult result) {
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content c : result.content()) {
            if (c instanceof McpSchema.TextContent t) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    private String authTokenOf(Map<String, Object> typeConfig) {
        Map<String, Object> auth = asMap(typeConfig.get("auth"));
        if (!"bearer".equals(auth.get("type"))) return null;
        return str(auth.get("tokenEnc"));
    }

    private String decryptOrNull(String enc) {
        if (enc == null || enc.isBlank()) return null;
        try {
            return aes.decrypt(enc);
        } catch (Exception e) {
            log.error("[Mcp] Failed to decrypt the configured MCP server credential: {}", e.getMessage());
            return null;
        }
    }

    private static final Set<String> TICKET_COLUMN_FIELDS = Set.of("title", "description", "status", "priority");

    private Object resolveInputField(TicketEntity ticket, WorkflowItemEntity item, String key) {
        if (key.startsWith("this.")) {
            if (item == null || item.getFieldValues() == null) return null;
            return item.getFieldValues().get(key.substring("this.".length()));
        }
        // "ticket." is optional — see ExternalApiActionExecutor.resolveInputField for why (backward
        // compatibility with older saved configs that store a bare key with no prefix at all).
        String ticketKey = key.startsWith("ticket.") ? key.substring("ticket.".length()) : key;
        return switch (ticketKey) {
            case "title" -> ticket.getTitle();
            case "description" -> ticket.getDescription();
            case "status" -> ticket.getStatus();
            case "priority" -> ticket.getPriority();
            default -> ticket.getTicketData() != null ? ticket.getTicketData().get(ticketKey) : null;
        };
    }

    /** Returns true if it wrote to the ticket (caller must save). Mirrors ExternalApiActionExecutor.applyTarget exactly — kept as its own small copy rather than a shared utility, consistent with this codebase's preference for small independently-readable services over premature shared abstractions. */
    private boolean applyTarget(WorkflowItemEntity item, TicketEntity ticket, String target, Object value) {
        String stringValue = value != null ? String.valueOf(value) : null;
        if (target.startsWith("this.")) {
            String key = target.substring("this.".length());
            Map<String, Object> fv = item.getFieldValues();
            if (fv == null) { fv = new LinkedHashMap<>(); item.setFieldValues(fv); }
            fv.put(key, stringValue);
            return false;
        }
        if (target.startsWith("ticket.")) {
            String key = target.substring("ticket.".length());
            if (TICKET_COLUMN_FIELDS.contains(key)) {
                switch (key) {
                    case "title" -> ticket.setTitle(stringValue);
                    case "description" -> ticket.setDescription(stringValue);
                    case "status" -> ticket.setStatus(stringValue);
                    case "priority" -> ticket.setPriority(stringValue);
                }
            } else {
                Map<String, Object> td = ticket.getTicketData();
                if (td == null) { td = new LinkedHashMap<>(); ticket.setTicketData(td); }
                td.put(key, value);
            }
            return true;
        }
        log.warn("[Mcp] Response mapping target '{}' doesn't start with 'ticket.' or 'this.' — ignored", target);
        return false;
    }

    private void fail(WorkflowItemEntity item, String message) {
        item.setStatus("blocked");
        item.setLastError(message);
        itemRepo.save(item);
        adminInboxService.createIfAbsent(item.getTicketId(), "wf_mcp_fail_" + item.getId(), "WORKFLOW_ACTION_FAILED",
                "MCP action item failed: " + item.getTitle() + " [TT-" + item.getTicketId() + "] — " + message);
        log.error("[Mcp] Item {} blocked: {}", item.getId(), message);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object e : list) if (e instanceof Map) result.add((Map<String, Object>) e);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private String str(Object o) {
        return o instanceof String s ? s : null;
    }
}
