package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.turbotikects.turbotikectsserver.dto.AiRefineCallInputDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketActivityLogRepository;
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
 * { "serverUrl": "https://...", "auth": {"type":"none"|"bearer"|"api_key", "headerName" (api_key
 *   only, default "X-API-Key"), "tokenEnc" (server-managed)},
 *   "calls": [ { "id", "order", "toolName",
 *                "argumentMappings": [{"toolArgument","ticketField"} | {"toolArgument","captureName"}
 *                                     | {"toolArgument","literalValue"}],
 *                "responseCaptures": [{"name","resultPath"}] } ],
 *   "fieldMappings": { "response": [{"captureName","target"}] } }
 * }
 *
 * Simpler than external_api's shape in one respect: MCP tool arguments are a structured
 * Map&lt;String,Object&gt;, not a string template, so each argumentMapping declares its source
 * directly (a ticket field, an earlier call's captured value, or a fixed admin-typed constant —
 * see coerceLiteral) rather than going through a {{placeholder}} indirection layer. A "ticketField"
 * value prefixed "this." reads from the item's
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
    // Same reasoning/value as ExternalApiActionExecutor's identical constant: bounded-but-large
    // slice of the real response for the "Auto-map from this response" AI feature.
    private static final int MAX_RAW_RESPONSE_FOR_TRACE_CHARS = 200_000;

    private final WorkflowItemRepository itemRepo;
    private final TicketRepository ticketRepo;
    private final TicketActivityLogRepository activityLogRepo;
    private final AesEncryptionUtils aes;
    private final AdminInboxService adminInboxService;
    private final McpClientService mcpClientService;
    private final McpServerService mcpServerService;
    private final WorkflowService workflowService;
    private final FieldDefinitionsRepository fieldDefinitionsRepo;
    private final AiSettingsService aiSettingsService;

    public McpActionExecutor(WorkflowItemRepository itemRepo, TicketRepository ticketRepo,
                              TicketActivityLogRepository activityLogRepo,
                              AesEncryptionUtils aes, AdminInboxService adminInboxService,
                              McpClientService mcpClientService,
                              McpServerService mcpServerService,
                              @Lazy WorkflowService workflowService,
                              FieldDefinitionsRepository fieldDefinitionsRepo,
                              AiSettingsService aiSettingsService) {
        this.itemRepo = itemRepo;
        this.ticketRepo = ticketRepo;
        this.activityLogRepo = activityLogRepo;
        this.aes = aes;
        this.adminInboxService = adminInboxService;
        this.mcpClientService = mcpClientService;
        this.mcpServerService = mcpServerService;
        this.workflowService = workflowService;
        this.fieldDefinitionsRepo = fieldDefinitionsRepo;
        this.aiSettingsService = aiSettingsService;
    }

    public void execute(WorkflowItemEntity item) {
        Long itemId = item.getId();
        new Thread(() -> runSequence(itemId), "mcp-action-exec-" + itemId).start();
    }

    /**
     * FEAT-06 Phase 7 — "test this call now". Mirrors ExternalApiActionExecutor.testRun exactly:
     * reuses the real runCall unchanged against a throwaway in-memory ticket, no persistence.
     * authType/headerName/token are passed in explicitly (rather than decrypted from typeConfig
     * here) because the Designer's in-memory draft may hold either a freshly-typed plaintext token
     * (not yet saved/encrypted) or nothing at all if testing an unmodified already-saved node — the
     * caller (WorkflowActionTestService) resolves which one applies before calling this.
     */
    public WorkflowActionTestResult testRun(Map<String, Object> typeConfig, String authType, String headerName, String token, Map<String, String> sampleTicketFields) {
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

            client = mcpClientService.openClient(serverUrl, authType, headerName, token);
            Map<String, Object> vars = new LinkedHashMap<>();
            for (Map<String, Object> call : calls) {
                // No WorkflowItemEntity exists in test mode — a "this." source always reads as empty.
                runCall(client, call, syntheticTicket, null, vars, trace, null);
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

            String authType;
            String headerName;
            String token;
            Map<String, Object> auth = asMap(typeConfig.get("auth"));
            // "saved_external" is a sentinel auth type (never sent to McpClientService) meaning
            // "resolve serverUrl+auth live from this saved external server (McpServersManager,
            // MANAGE_FIELDS-gated) by id" instead of using the embedded serverUrl/auth snapshot
            // below — needed because OAuth2 connection auth genuinely expires and must be
            // resolved/refreshed fresh at call time, not baked in once when the item's action was
            // configured. Absent for every pre-existing workflow and for built-in-server picks,
            // which keep using the embedded snapshot exactly as before. Kept inside the existing
            // "auth" map (rather than a new top-level typeConfig field) so no frontend prop needed
            // threading through WorkflowDesignerModal/AiWorkflowBuilderPage/ActionItemLibraryPage —
            // the existing auth/onAuthChange plumbing already reaches every caller.
            if ("saved_external".equals(str(auth.get("type"))) && auth.get("mcpServerId") instanceof Number n) {
                McpServerService.ResolvedAuth resolved = mcpServerService.resolveConnectionAuth(n.longValue());
                serverUrl = resolved.serverUrl();
                authType = resolved.authType();
                headerName = resolved.headerName();
                token = resolved.token();
            } else {
                authType = str(auth.get("type"));
                headerName = str(auth.get("headerName"));
                token = decryptOrNull(authTokenOf(typeConfig));
            }
            client = mcpClientService.openClient(serverUrl, authType, headerName, token);

            // Collects non-fatal capture/mapping problems (a resultPath that matched nothing, a
            // response mapping referencing a capture that never populated) — the tool call itself
            // can fully succeed while this still silently produces no field update, which is
            // confusing with no visible signal at all (found live: an admin's "echo" call
            // completed as "done" with the ticket description never updated, root-caused to a
            // resultPath of "$.data" against a plain-text tool response, which this app wraps as
            // {"text": ...} — "$.text" was the correct path). Surfaced via lastError even on a
            // "done" item rather than only ever logged server-side, where no admin would see it.
            List<String> warnings = new ArrayList<>();

            Map<String, Object> vars = new LinkedHashMap<>();
            boolean anyCaptureConfigured = false;
            for (Map<String, Object> call : calls) {
                if (!listOf(call.get("responseCaptures")).isEmpty()) anyCaptureConfigured = true;
                runCall(client, call, ticket, item, vars, null, warnings);
            }

            // Mirrors evaluateResponseCaptures' identical fix: a tool call can come back
            // isError=false (the script itself caught a downstream failure — e.g. the target API
            // rejected the request — and returned a normal JSON error payload instead of throwing)
            // while EVERY configured capture still matches nothing, because none of the expected
            // fields exist in that error payload. Previously this still landed as "done" with the
            // real problem buried in lastError — found live on a real ticket where every capture on
            // a formally-successful search_google_flights call failed because the API rejected an
            // invalid date range, yet the item showed as completed.
            if (anyCaptureConfigured && vars.isEmpty() && !warnings.isEmpty()) {
                fail(item, String.join("; ", warnings));
                return;
            }

            // A real bug found live: the "ticket" loaded at the top of this method can be stale by
            // the time this point is reached — the tool call sequence above can take seconds, and
            // this item's own activation is itself commonly triggered by the SAME request that just
            // changed the ticket's status (see WorkflowService.cascadeTicketStatus, spawned from
            // TicketService.patch() on a background thread BEFORE that request's transaction
            // commits). Writing back the entity loaded here would silently clobber that concurrent
            // status change back to its old value (Hibernate's merge() on a detached entity does a
            // full-column UPDATE, not just the touched fields, and TicketEntity has no real @Version
            // optimistic lock to catch this). Re-fetching immediately before the write closes that
            // window in practice — the call sequence above takes far longer than the handful of
            // synchronous statements TicketService.patch() has left to run before it commits.
            TicketEntity freshTicket = ticketRepo.findById(item.getTicketId()).orElse(ticket);

            // Which target fields are "nodelist"-typed (an admin-editable list of readable text
            // entries — see TicketFormRenderer.tsx's NodeListControl) — determines whether a
            // response mapping below OVERWRITES the target (every other field type) or APPENDS one
            // new node per mapped value (nodelist only). MCP tool results are commonly a structured
            // JSON object/array already (vars holds the raw, un-stringified capture — see runCall
            // above), so this is exactly where a captured object needs to be humanized rather than
            // dumped as a raw Map.toString().
            Map<String, String> ticketFieldTypes = fieldTypesByEntity("ticket");
            Map<String, String> workflowFieldTypes = fieldTypesByEntity("workflow");

            boolean ticketChanged = false;
            Map<String, Object> changes = new LinkedHashMap<>();
            Map<String, Object> fieldMappings = asMap(typeConfig.get("fieldMappings"));
            for (Map<String, Object> respMap : listOf(fieldMappings.get("response"))) {
                String captureName = str(respMap.get("captureName"));
                String target = str(respMap.get("target"));
                if (captureName == null || target == null) continue;
                if (!vars.containsKey(captureName)) {
                    warnings.add("Response mapping to '" + target + "' was skipped — capture '" + captureName + "' was never populated");
                    continue;
                }
                String fieldType = target.startsWith("this.") ? workflowFieldTypes.get(target.substring("this.".length()))
                        : target.startsWith("ticket.") ? ticketFieldTypes.get(target.substring("ticket.".length())) : null;
                Object oldValue = target.startsWith("ticket.") ? resolveInputField(freshTicket, item, target) : null;

                boolean changed;
                Object loggedValue;
                if ("nodelist".equals(fieldType)) {
                    List<String> nodesToAdd = toNodeStrings(vars.get(captureName));
                    if (nodesToAdd.isEmpty()) {
                        warnings.add("Response mapping to '" + target + "' was skipped — capture '" + captureName + "' resolved to an empty value");
                        continue;
                    }
                    changed = applyNodelistTarget(item, freshTicket, target, nodesToAdd);
                    loggedValue = String.join("; ", nodesToAdd);
                } else {
                    Object newValue = vars.get(captureName);
                    changed = applyTarget(item, freshTicket, target, newValue);
                    loggedValue = newValue;
                }
                if (changed) {
                    ticketChanged = true;
                    // FieldUpdateCard.tsx keys its FIELD_LABELS/status/priority special-casing by
                    // the bare field name ("description", not "ticket.description") — strip the
                    // prefix so the entry renders with a proper label instead of a raw key.
                    String changeKey = target.startsWith("ticket.") ? target.substring("ticket.".length()) : target;
                    changes.put(changeKey, Map.of("from", oldValue != null ? oldValue : "", "to", loggedValue != null ? loggedValue : ""));
                }
            }
            if (ticketChanged) {
                ticketRepo.save(freshTicket);
                writeActivityLog(freshTicket, item, changes);
            }

            item.setStatus("done");
            item.setLastError(warnings.isEmpty() ? null : String.join("; ", warnings));
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

    /**
     * A real gap found live alongside the stale-ticket bug above: this executor wrote ticket field
     * changes straight through TicketRepository with no activity-log entry at all — unlike every
     * other automated ticket mutator in this codebase (e.g. AccelerationSchedulerService.writeLog),
     * which all record an actorId=0/"system" entry so the change is visible in the ticket's own
     * Activity Log, not just silently applied. Mirrors that established pattern exactly.
     */
    private void writeActivityLog(TicketEntity ticket, WorkflowItemEntity item, Map<String, Object> changes) {
        if (changes.isEmpty()) return;
        TicketActivityLogEntity entry = new TicketActivityLogEntity();
        entry.setTicketId(ticket.getId());
        entry.setActorId(0); // 0 = system sentinel (no FK constraint in DB)
        entry.setOperation("WORKFLOW_ACTION_APPLIED");
        entry.setActivityType("system");
        entry.setChanges(changes);
        entry.setMetadata(Map.of("itemId", item.getId(), "itemTitle", item.getTitle(), "itemType", item.getType()));
        activityLogRepo.save(entry);
    }

    /**
     * trace is non-null only in {@link #testRun} — appended to for the "test this call now" result
     * panel, ignored (null) during real item execution. warnings is non-null only during real item
     * execution — {@link #testRun} skips it since its capturedValues already make a missing capture
     * visible to the admin interactively; the real execution path has no such interactive feedback,
     * so runSequence surfaces these via the item's lastError instead.
     */
    private void runCall(McpSyncClient client, Map<String, Object> call, TicketEntity ticket, WorkflowItemEntity item, Map<String, Object> vars, List<Map<String, Object>> trace, List<String> warnings) {
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
            // A real gap found live: some tool parameters are always the same fixed value for
            // every ticket (e.g. a hotel-search tool's "rooms" parameter, since this app only ever
            // books one room per ticket) — forcing every argument through "from ticket field" or
            // "from earlier capture" left no way to express that without inventing a fake constant
            // ticket field just to hold it. literalValue is a third, explicit source: an
            // admin-typed constant, coerced to a real number/boolean when it looks like one so a
            // typed schema (e.g. "rooms": integer) validates correctly — see coerceLiteral.
            if (mapping.get("literalValue") != null) {
                arguments.put(toolArgument, coerceLiteral(String.valueOf(mapping.get("literalValue"))));
            } else if (captureName != null) {
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
                // Omit the key entirely rather than putting an explicit null: a tool parameter
                // typed e.g. "type: str = None" (optional, defaults to None if the caller never
                // supplies it) still gets strictly validated against "str" by the MCP SDK's
                // generated Pydantic model when the argument IS present in the call, even with a
                // null value — an explicit null fails validation where an absent key falls back
                // to the Python-side default and succeeds. A blank ticket/action-item field must
                // behave like "not supplied", not like "supplied as null".
                Object value = resolveInputField(ticket, item, ticketField);
                if (value != null) {
                    arguments.put(toolArgument, value);
                }
            }
        }

        log.info("[Mcp] Calling tool '{}' with arguments {}", toolName, arguments.keySet());
        McpSchema.CallToolResult result = mcpClientService.callTool(client, toolName, arguments);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new RuntimeException("Tool '" + toolName + "' reported an error: " + extractText(result));
        }

        Object resultObject = buildResultObject(result);
        for (Map<String, Object> cap : listOf(call.get("responseCaptures"))) {
            applyResponseCapture(resultObject, cap, toolName, vars, warnings);
        }

        if (trace != null) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("callId", str(call.get("id")));
            entry.put("name", toolName);
            entry.put("request", "tool:" + toolName + " args=" + arguments.keySet());
            entry.put("status", Boolean.TRUE.equals(result.isError()) ? "error" : "ok");
            // A real gap found live: this previously only ever included extractText(result) (plain
            // TextContent, concatenated) — but the actual JSONPath captures above run against
            // resultObject (buildResultObject prefers structuredContent over text), so for any tool
            // returning structured (non-text) content, the trace an admin/LLM saw could be empty or
            // unrelated to what was really captured. "rawResult" now carries the literal object
            // captures actually ran against; "responsePreview" stays as the plain-text-only view.
            entry.put("responsePreview", extractText(result));
            entry.put("rawResult", rawResultForTrace(resultObject));
            trace.add(entry);
        }
    }

    /**
     * The single dispatch point for "how a response capture resolves" — shared by a real live call
     * ({@link #runCall}) and the "Verify Mapping" dry-run ({@link #evaluateResponseCaptures}), so
     * both ever have exactly one implementation. Mirrors ExternalApiActionExecutor's identical
     * applyResponseCaptures/applyJsonPathCapture/applyLlmCapture split. Absent "mode" behaves
     * exactly as "jsonpath" — every capture saved before "ai_summary" existed keeps resolving
     * identically.
     */
    private void applyResponseCapture(Object resultObject, Map<String, Object> cap, String toolName,
                                       Map<String, Object> vars, List<String> warnings) {
        String name = str(cap.get("name"));
        String resultPath = str(cap.get("resultPath"));
        if (name == null || resultPath == null) return;
        String mode = Optional.ofNullable(str(cap.get("mode"))).orElse("jsonpath");
        if ("ai_summary".equals(mode)) {
            applyAiSummaryCapture(resultObject, resultPath, str(cap.get("summaryInstruction")), name, toolName, vars, warnings);
            return;
        }
        try {
            Object extracted = JsonPath.read(resultObject, resultPath);
            vars.put(name, extracted);
        } catch (Exception e) {
            log.warn("[Mcp] Capture '{}' (resultPath {}) failed on tool '{}': {}", name, resultPath, toolName, e.getMessage());
            if (warnings != null) {
                warnings.add("Capture '" + name + "' (resultPath " + resultPath + ") on tool '" + toolName + "' matched nothing in the response");
            }
        }
    }

    /**
     * "AI Summary" capture — the admin selected a whole array/object branch in the Visual JSON
     * Explorer (not a single leaf, since there's no one scalar to JSONPath-extract from a branch)
     * and asked the AI to turn it into a human-readable HTML summary for a rich-text field. Calls
     * the active AI provider LIVE every time this capture resolves — both here (a real ticket
     * execution) and from {@link #evaluateResponseCaptures} ("Verify Mapping", a dry run against the
     * already-captured response) — same deliberate exception as ExternalApiActionExecutor's "AI:
     * describe what to extract" mode: every other capture mechanism in this class is 100%
     * deterministic on purpose, this one mode is the sole, intentional live-AI-call exception. No
     * active provider, or any failure, degrades exactly like a JsonPath miss — a warning, never a
     * crash of the whole call/item.
     */
    private void applyAiSummaryCapture(Object resultObject, String resultPath, String summaryInstruction,
                                        String name, String toolName, Map<String, Object> vars, List<String> warnings) {
        Object branch;
        try {
            branch = JsonPath.read(resultObject, resultPath);
        } catch (Exception e) {
            log.warn("[Mcp] AI Summary capture '{}' (resultPath {}) failed on tool '{}': {}", name, resultPath, toolName, e.getMessage());
            if (warnings != null) {
                warnings.add("Capture '" + name + "' (resultPath " + resultPath + ") on tool '" + toolName + "' matched nothing in the response");
            }
            return;
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            log.warn("[Mcp] AI Summary capture '{}' on tool '{}' skipped — no active AI provider configured", name, toolName);
            if (warnings != null) {
                warnings.add("Capture '" + name + "' (AI Summary) on tool '" + toolName + "' was skipped — no active AI provider is configured");
            }
            return;
        }
        Optional<String> html = aiSettingsService.summarizeAsHtml(aiSettings, branch, summaryInstruction);
        if (html.isEmpty()) {
            if (warnings != null) {
                warnings.add("Capture '" + name + "' (AI Summary) on tool '" + toolName + "' — the AI could not summarize this data");
            }
            return;
        }
        vars.put(name, html.get());
    }

    /** Bounded JSON-serialized slice of the real object captures ran against, for the "Auto-map from this response" AI feature. */
    private String rawResultForTrace(Object resultObject) {
        try {
            String json = new ObjectMapper().writeValueAsString(resultObject);
            return json.length() > MAX_RAW_RESPONSE_FOR_TRACE_CHARS ? json.substring(0, MAX_RAW_RESPONSE_FOR_TRACE_CHARS) : json;
        } catch (Exception e) {
            return "";
        }
    }

    /** Assembles a JSONPath-queryable object from a CallToolResult: prefers structuredContent, falls back to {@link #parseAsQueryableObject} against the concatenated text content — either way, run through {@link #deepUnwrapJsonStrings} first (see its own javadoc for why). */
    private Object buildResultObject(McpSchema.CallToolResult result) {
        Object base = result.structuredContent() != null ? result.structuredContent() : parseAsQueryableObject(extractText(result));
        return deepUnwrapJsonStrings(base, 0);
    }

    /**
     * Real bug found live: a tool function typed to return a plain string (this codebase's
     * generated scripts commonly do — {@code def some_tool(...) -> str: return json.dumps(data)})
     * gets its structuredContent wrapped by the MCP Python SDK as a synthetic single-property
     * envelope, {@code {"result": "<the JSON, still escaped as one big string>"}} — MCP's structured
     * content requires an object schema, so a scalar return gets boxed rather than emitted as-is.
     * Without unwrapping, EVERY resultPath needs a "$.result." prefix AND still can't reach past
     * that string (JsonPath can't descend into an unparsed string value), and the "Map & Verify
     * Output" step's JSON tree showed one giant unreadable string leaf instead of a real tree —
     * exactly what an admin reported live. Recursively re-parses any string value that itself looks
     * like a JSON object/array, so both a real capture's resultPath AND the tree an admin clicks
     * through see the SAME fully-unwrapped structure, at any nesting depth (not just the top level —
     * some MCP servers double-encode more than one layer deep). depth is a runaway guard only; real
     * responses are never anywhere close to 10 levels of string-in-string encoding.
     */
    @SuppressWarnings("unchecked")
    private Object deepUnwrapJsonStrings(Object value, int depth) {
        if (depth > 10) return value;
        if (value instanceof String s) {
            String trimmed = s.trim();
            boolean looksLikeJson = (trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"));
            if (!looksLikeJson) return value;
            try {
                Object parsed = JsonPath.parse(trimmed).json();
                if (parsed instanceof Map || parsed instanceof List) return deepUnwrapJsonStrings(parsed, depth + 1);
            } catch (Exception ignored) {
                // not actually JSON despite looking like it (e.g. a price range "{1-5}") — leave as-is
            }
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object v : list) out.add(deepUnwrapJsonStrings(v, depth + 1));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) out.put(String.valueOf(e.getKey()), deepUnwrapJsonStrings(e.getValue(), depth + 1));
            return out;
        }
        return value;
    }

    /**
     * Parses raw text as JSON, falling back to {"text": <text>} so $.text always works as an
     * escape hatch — shared by {@link #buildResultObject} (a real live call's result) and
     * {@link #evaluateResponseCaptures} (re-evaluating an already-captured rawResponse string, no
     * new call), so "how a response becomes queryable" has exactly one implementation used by both
     * a real execution and its "Verify Mapping" dry-run counterpart — the same guarantee
     * ExternalApiActionExecutor.evaluateResponseCaptures already relies on via applyResponseCaptures.
     */
    private Object parseAsQueryableObject(String text) {
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

    /**
     * "Verify Mapping" — AI Workflow Builder's Map & Verify Output step, mcp_tool sibling of
     * ExternalApiActionExecutor.evaluateResponseCaptures (see its javadoc for the full rationale).
     * Re-evaluates each call's (AI-proposed, tree-click-added, or admin-edited) responseCaptures
     * against a response ALREADY captured by an earlier "Test this call now" run — no new live MCP
     * tool call. rawResponse here is whatever TestActionModal's call trace stored for that call
     * (rawResult/rawResponse — the literal object/text the real run captured against), so this uses
     * the exact same {@link #parseAsQueryableObject}+{@code JsonPath.read} pair a real execution
     * would, guaranteeing this dry-run agrees with what would actually happen on a real ticket.
     */
    public WorkflowActionTestResult evaluateResponseCaptures(List<AiRefineCallInputDto> calls) {
        Map<String, Object> vars = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> trace = new ArrayList<>();
        boolean anyCaptureConfigured = false;
        for (AiRefineCallInputDto c : calls) {
            if (c.getRawResponse() == null || c.getRawResponse().isBlank()) continue;
            List<Map<String, Object>> captures = c.getExistingResponseCaptures() != null ? c.getExistingResponseCaptures() : List.of();
            if (!captures.isEmpty()) anyCaptureConfigured = true;
            Object resultObject = deepUnwrapJsonStrings(parseAsQueryableObject(c.getRawResponse()), 0);
            for (Map<String, Object> cap : captures) {
                applyResponseCapture(resultObject, cap, c.getName(), vars, warnings);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("callId", c.getId());
            entry.put("name", c.getName());
            trace.add(entry);
        }
        // Mirrors ExternalApiActionExecutor.evaluateResponseCaptures' identical fix: a totally-empty
        // result (every capture matched nothing) must be reported as a real failure, not silently
        // read as "verified fine" by the wizard.
        if (anyCaptureConfigured && vars.isEmpty() && !warnings.isEmpty()) {
            return WorkflowActionTestResult.failure(String.join("; ", warnings), trace);
        }
        WorkflowActionTestResult result = WorkflowActionTestResult.success(new LinkedHashMap<>(vars), trace);
        if (!warnings.isEmpty()) result.setError(String.join("; ", warnings));
        return result;
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
        String type = str(auth.get("type"));
        if (!"bearer".equals(type) && !"api_key".equals(type)) return null;
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

    /** A hardcoded argument value is always typed as a string by the admin (a plain text input,
     * per McpToolCallsEditor.tsx) — coerce it to a real number/boolean when it unambiguously looks
     * like one, so a tool parameter typed e.g. "rooms": integer in its MCP schema still validates.
     * Anything else (including something that merely starts with a digit, like a hotel id
     * "TP-PH-1") stays a plain string. */
    private Object coerceLiteral(String raw) {
        if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (raw.matches("-?\\d+")) {
            try { return Long.parseLong(raw); } catch (NumberFormatException ignored) { /* falls through to string */ }
        } else if (raw.matches("-?\\d+\\.\\d+")) {
            try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) { /* falls through to string */ }
        }
        return raw;
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

    /** field_key -> field_type for every field_definitions row of the given entity_type ("ticket" or "workflow") — mirrors ExternalApiActionExecutor.fieldTypesByEntity exactly. */
    private Map<String, String> fieldTypesByEntity(String entityType) {
        Map<String, String> types = new HashMap<>();
        for (FieldDefinitionsEntity f : fieldDefinitionsRepo.findByEntityTypeOrderByDisplayOrder(entityType)) {
            types.put(f.getFieldKey(), f.getFieldType());
        }
        return types;
    }

    /** Mirrors ExternalApiActionExecutor.toNodeStrings exactly — a JSON array captured by an MCP tool result becomes one humanized node per element; a single object/scalar becomes exactly one node. */
    private List<String> toNodeStrings(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> nodes = new ArrayList<>();
            for (Object element : list) {
                String node = humanizeJsonValue(element);
                if (!node.isBlank()) nodes.add(node);
            }
            return nodes;
        }
        String node = humanizeJsonValue(raw);
        return node.isBlank() ? List.of() : List.of(node);
    }

    /** "take json break it into human readable data as string" — mirrors ExternalApiActionExecutor.humanizeJsonValue exactly: flattens a JSON object/array into a plain "key: value, key2: value2" line instead of a raw Map.toString()/JSON dump. */
    private String humanizeJsonValue(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(humanizeJsonValue(entry.getValue()));
            }
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object element : list) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(humanizeJsonValue(element));
            }
            return sb.toString();
        }
        return String.valueOf(value);
    }

    /** Appends (never overwrites) — mirrors ExternalApiActionExecutor.appendNodes exactly. */
    @SuppressWarnings("unchecked")
    private void appendNodes(Map<String, Object> data, String key, List<String> nodesToAdd) {
        List<Object> nodes = data.get(key) instanceof List<?> existing ? new ArrayList<>(existing) : new ArrayList<>();
        nodes.addAll(nodesToAdd);
        data.put(key, nodes);
    }

    /** nodelist counterpart to {@link #applyTarget} — same target grammar, append-only semantics instead of overwrite. Returns true if it wrote to the ticket (caller must save), matching applyTarget's contract. */
    private boolean applyNodelistTarget(WorkflowItemEntity item, TicketEntity ticket, String target, List<String> nodesToAdd) {
        if (target.startsWith("this.")) {
            Map<String, Object> fv = item.getFieldValues();
            if (fv == null) { fv = new LinkedHashMap<>(); item.setFieldValues(fv); }
            appendNodes(fv, target.substring("this.".length()), nodesToAdd);
            return false;
        }
        if (target.startsWith("ticket.")) {
            String key = target.substring("ticket.".length());
            Map<String, Object> td = ticket.getTicketData();
            if (td == null) { td = new LinkedHashMap<>(); ticket.setTicketData(td); }
            appendNodes(td, key, nodesToAdd);
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
