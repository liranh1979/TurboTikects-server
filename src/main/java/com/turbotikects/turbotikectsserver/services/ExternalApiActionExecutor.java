package com.turbotikects.turbotikectsserver.services;

import com.jayway.jsonpath.JsonPath;
import com.turbotikects.turbotikectsserver.dto.AiRefineCallInputDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketActivityLogRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.WorkflowItemRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes 'external_api' workflow items (FEAT-06 Phase 4) — an admin-configured, ordered sequence
 * of HTTP calls run the moment the item activates (no human action needed to complete it). A
 * declarative JSON typeConfig interpreted here by plain Java, deliberately NOT extending
 * AccelerationGroovyEngine's unsandboxed-eval model for this admin-authored-but-network-reaching
 * config — see the FEAT-06 plan's Phase 4 section for the full reasoning (don't inject an HTTP
 * client into an unsandboxed eval context, don't generate Groovy strings with admin-supplied
 * URLs/headers baked in).
 *
 * typeConfig shape:
 * {@code
 * { "calls": [ { "id", "order", "name", "method", "urlTemplate",
 *                "headers": [{"key","valueTemplate"}],
 *                "auth": {"type":"none"|"bearer"|"api_key"|"basic",
 *                         "tokenEnc"/"usernameEnc"/"passwordEnc" (server-managed), "headerName"},
 *                "bodyTemplate", "responseCaptures": [{"name","jsonPath"}] } ],
 *   "fieldMappings": { "request": [{"placeholder","ticketField"}],
 *                       "response": [{"captureName","target"}] } }
 * }
 *
 * "ticketField"/"target" grammar: "title"|"description"|"status"|"priority" resolve to the ticket's
 * actual columns; anything else resolves to ticket.ticketData[key]. A "this." prefix (on either a
 * request-side ticketField or a response target) instead reads/writes the item's own field_values —
 * e.g. request ticketField "this.laptop_model" pulls a value the admin/assignee already filled in
 * (or an earlier call in this same sequence captured) via SimpleItemFieldsForm/an earlier response
 * mapping; response target "this.provisioned_id" writes a fresh one there.
 *
 * Captured values accumulate into one flat vars map across the whole call sequence — a later call's
 * templates can reference an earlier call's capture directly via {{captureName}} (no per-call
 * namespace prefix needed, since capture names are admin-chosen and expected unique within one
 * item's typeConfig — simpler than a "calls.&lt;name&gt;.&lt;field&gt;" grammar for the same result).
 */
@Slf4j
@Service
public class ExternalApiActionExecutor {

    private static final int MAX_CALLS = 10;
    private static final int REQUEST_TIMEOUT_SECONDS = 15;
    private static final int MAX_RESPONSE_CHARS = 1_000_000; // ~1MB of text
    // A real bug found live: the trace's "responsePreview" (used both for the admin's own eyes and,
    // as of the new "Auto-map from this response" feature, as the actual ground truth handed to an
    // LLM) was capped at 300/2000 chars — nowhere near enough for a real API's JSON body, causing
    // the AI to only ever see a truncated fragment. This separate, much larger cap keeps the LLM
    // request itself bounded (well under the existing 1MB capture-time MAX_RESPONSE_CHARS) while
    // being large enough for real payloads (SerpApi-shaped flight-search JSON and similar).
    private static final int MAX_RAW_RESPONSE_FOR_TRACE_CHARS = 200_000;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private final WorkflowItemRepository itemRepo;
    private final TicketRepository ticketRepo;
    private final TicketActivityLogRepository activityLogRepo;
    private final AesEncryptionUtils aes;
    private final AdminInboxService adminInboxService;
    private final WorkflowService workflowService;
    private final FieldDefinitionsRepository fieldDefinitionsRepo;

    // @Lazy on WorkflowService only — mirrors the WorkflowService<->ApprovalService cycle fix
    // (WorkflowService.activateItem dispatches INTO this class, this class calls back into
    // WorkflowService.onItemCompleted when the sequence finishes).
    public ExternalApiActionExecutor(WorkflowItemRepository itemRepo, TicketRepository ticketRepo,
                                      TicketActivityLogRepository activityLogRepo,
                                      AesEncryptionUtils aes, AdminInboxService adminInboxService,
                                      @Lazy WorkflowService workflowService,
                                      FieldDefinitionsRepository fieldDefinitionsRepo) {
        this.itemRepo = itemRepo;
        this.ticketRepo = ticketRepo;
        this.activityLogRepo = activityLogRepo;
        this.aes = aes;
        this.adminInboxService = adminInboxService;
        this.workflowService = workflowService;
        this.fieldDefinitionsRepo = fieldDefinitionsRepo;
    }

    /** Called from WorkflowService.activateItem the moment an external_api item becomes in_progress. Runs on a background thread — never blocks the caller's transaction with network I/O. */
    public void execute(WorkflowItemEntity item) {
        Long itemId = item.getId();
        new Thread(() -> runSequence(itemId), "external-api-exec-" + itemId).start();
    }

    /**
     * FEAT-06 Phase 7 — "test this call now". Runs the exact same {@link #runCall} used by real
     * item activation, synchronously, against a throwaway in-memory ticket built from
     * admin-supplied sample field values — no WorkflowItemEntity, no persistence, nothing written
     * anywhere. Deliberately reuses runCall unchanged rather than a parallel test-mode
     * implementation, so a passing test is evidence about the exact code path that will run for
     * real, not a second, potentially-diverging one.
     */
    public WorkflowActionTestResult testRun(Map<String, Object> typeConfig, Map<String, String> sampleTicketFields) {
        List<Map<String, Object>> trace = new ArrayList<>();
        try {
            List<Map<String, Object>> calls = new ArrayList<>(listOf(typeConfig != null ? typeConfig.get("calls") : null));
            if (calls.isEmpty()) return WorkflowActionTestResult.failure("No calls configured", trace);
            if (calls.size() > MAX_CALLS) return WorkflowActionTestResult.failure("Too many calls configured", trace);
            calls.sort(Comparator.comparingInt(c -> c.get("order") != null ? ((Number) c.get("order")).intValue() : 0));

            TicketEntity syntheticTicket = new TicketEntity();
            syntheticTicket.setTitle(sampleTicketFields.getOrDefault("title", ""));
            syntheticTicket.setDescription(sampleTicketFields.get("description"));
            syntheticTicket.setStatus(sampleTicketFields.getOrDefault("status", "new"));
            syntheticTicket.setPriority(sampleTicketFields.getOrDefault("priority", "medium"));
            Map<String, Object> ticketData = new LinkedHashMap<>(sampleTicketFields);
            syntheticTicket.setTicketData(ticketData);

            Map<String, String> vars = new LinkedHashMap<>();
            Map<String, Object> fieldMappings = typeConfig != null ? asMap(typeConfig.get("fieldMappings")) : Map.of();
            for (Map<String, Object> reqMap : listOf(fieldMappings.get("request"))) {
                String placeholder = str(reqMap.get("placeholder"));
                String ticketField = str(reqMap.get("ticketField"));
                if (placeholder == null || ticketField == null) continue;
                // No WorkflowItemEntity exists in test mode, so a "this." source always reads as empty here —
                // an accepted limitation of testing against a synthetic ticket with no real item behind it.
                Object value = resolveInputField(syntheticTicket, null, ticketField);
                vars.put(placeholder, value != null ? String.valueOf(value) : "");
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build();
            for (Map<String, Object> call : calls) {
                runCall(client, call, vars, trace, null, null);
            }
            return WorkflowActionTestResult.success(new LinkedHashMap<>(vars), trace);
        } catch (Exception e) {
            return WorkflowActionTestResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), trace);
        }
    }

    private void runSequence(Long itemId) {
        WorkflowItemEntity item = itemRepo.findById(itemId).orElse(null);
        if (item == null) return;
        try {
            Map<String, Object> typeConfig = item.getTypeConfig();
            List<Map<String, Object>> calls = new ArrayList<>(listOf(typeConfig != null ? typeConfig.get("calls") : null));
            if (calls.isEmpty()) {
                fail(item, "No calls configured on this action item");
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

            Map<String, String> vars = new LinkedHashMap<>();
            Map<String, Object> fieldMappings = typeConfig != null ? asMap(typeConfig.get("fieldMappings")) : Map.of();
            for (Map<String, Object> reqMap : listOf(fieldMappings.get("request"))) {
                String placeholder = str(reqMap.get("placeholder"));
                String ticketField = str(reqMap.get("ticketField"));
                if (placeholder == null || ticketField == null) continue;
                Object value = resolveInputField(ticket, item, ticketField);
                vars.put(placeholder, value != null ? String.valueOf(value) : "");
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .build();

            // See McpActionExecutor.runSequence's identical warnings mechanism for why this exists:
            // a call can fully succeed while its response capture jsonPath matches nothing, which
            // previously only ever logged server-side — silently leaving a "done" item with no
            // field actually updated and zero visible indication anything was wrong.
            List<String> warnings = new ArrayList<>();

            // Populated alongside "vars" (which flattens every capture down to a plain String for
            // {{placeholder}} substitution) with the RAW JsonPath.read result of each capture — a
            // capture whose jsonPath targets a JSON object/array (not a scalar leaf) needs its real
            // structure, not vars' stringified form, to be split into human-readable nodelist entries
            // below. See applyResponseCaptures/humanizeJsonValue.
            Map<String, Object> rawCaptured = new LinkedHashMap<>();

            for (Map<String, Object> call : calls) {
                runCall(client, call, vars, null, warnings, rawCaptured);
            }

            // A real bug found live: "ticket" (loaded above, before the potentially multi-second
            // HTTP call sequence) can be stale by this point — this item's own activation is
            // commonly triggered by the SAME request that just changed the ticket's status (see
            // WorkflowService.cascadeTicketStatus, spawned from TicketService.patch() on a
            // background thread BEFORE that request's transaction commits). Writing back the
            // entity loaded above would silently clobber that concurrent status change back to its
            // old value (Hibernate's merge() on a detached entity does a full-column UPDATE, not
            // just the touched fields, and TicketEntity has no real @Version optimistic lock to
            // catch this). Re-fetching immediately before the write closes that window in practice.
            TicketEntity freshTicket = ticketRepo.findById(item.getTicketId()).orElse(ticket);

            // Which target fields are "nodelist"-typed (an admin-editable list of readable text
            // entries — see TicketFormRenderer.tsx's NodeListControl) — determines whether a
            // response mapping below OVERWRITES the target (every other field type) or APPENDS one
            // new node per mapped value (nodelist only), per admin request: "the AI agent will
            // support all the field types include node_list, it need to know how to add node to the
            // list, take json break it into human readable data as string".
            Map<String, String> ticketFieldTypes = fieldTypesByEntity("ticket");
            Map<String, String> workflowFieldTypes = fieldTypesByEntity("workflow");

            boolean ticketChanged = false;
            Map<String, Object> changes = new LinkedHashMap<>();
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
                String loggedValue;
                if ("nodelist".equals(fieldType)) {
                    List<String> nodesToAdd = toNodeStrings(rawCaptured.getOrDefault(captureName, vars.get(captureName)));
                    if (nodesToAdd.isEmpty()) {
                        warnings.add("Response mapping to '" + target + "' was skipped — capture '" + captureName + "' resolved to an empty value");
                        continue;
                    }
                    changed = applyNodelistTarget(item, freshTicket, target, nodesToAdd);
                    loggedValue = String.join("; ", nodesToAdd);
                } else {
                    String newValue = vars.get(captureName);
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
            log.info("[ExternalApi] Item {} completed successfully ({} calls)", itemId, calls.size());
            workflowService.onItemCompleted(itemId, item.getTicketId());
        } catch (Exception e) {
            log.error("[ExternalApi] Item {} failed: {}", itemId, e.getMessage(), e);
            WorkflowItemEntity fresh = itemRepo.findById(itemId).orElse(null);
            if (fresh != null) fail(fresh, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
     * execution — see runSequence's javadoc-style comment above its declaration for why.
     */
    private void runCall(HttpClient client, Map<String, Object> call, Map<String, String> vars, List<Map<String, Object>> trace, List<String> warnings, Map<String, Object> rawCaptured) throws Exception {
        String callName = Optional.ofNullable(str(call.get("name"))).orElse("call");
        String method = Optional.ofNullable(str(call.get("method"))).orElse("GET").toUpperCase();
        String url = substituteForUrl(str(call.get("urlTemplate")), vars);
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Call '" + callName + "' has no URL configured");
        // A real bug found live: substituteForUrl's PLACEHOLDER regex only recognizes {{name}}
        // where name is exactly [a-zA-Z0-9_]+ — a placeholder containing anything else (a stray
        // hyphen, a hidden/non-standard whitespace character, often introduced by copy-pasting
        // vendor API docs) is silently left untouched rather than substituted, and since "{"/"}"
        // are themselves illegal URI characters, this previously surfaced only as a cryptic
        // "Illegal character in query at index N" from new URI(url) far below, with no hint that
        // the real cause was an unresolved placeholder. This broader check (not the strict
        // PLACEHOLDER regex) catches that class of leftover "{{"/"}}" text and fails clearly.
        if (url.contains("{{") || url.contains("}}")) {
            throw new IllegalArgumentException("Call '" + callName + "' has a URL with what looks like an unresolved \"{{...}}\" placeholder still present after substitution: " + url +
                    " — check for a typo, an unsupported character (only letters, numbers, and underscores are allowed inside {{ }}), or a hidden character possibly introduced by copy-pasting.");
        }
        url = appendAuthQueryParam(url, call.get("auth"));

        String body = substitute(str(call.get("bodyTemplate")), vars);
        HttpRequest.BodyPublisher bodyPublisher = (body == null || body.isBlank())
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .method(method, bodyPublisher);

        boolean hasContentType = false;
        for (Map<String, Object> h : listOf(call.get("headers"))) {
            String key = str(h.get("key"));
            if (key == null || key.isBlank()) continue;
            String val = substitute(str(h.get("valueTemplate")), vars);
            if ("content-type".equalsIgnoreCase(key)) hasContentType = true;
            builder.header(key, val != null ? val : "");
        }
        if (!hasContentType && body != null && !body.isBlank()) {
            builder.header("Content-Type", "application/json");
        }
        applyAuth(builder, call.get("auth"));

        log.info("[ExternalApi] Call '{}': {} {}", callName, method, url);
        HttpResponse<String> res = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = res.body();
        if (responseBody != null && responseBody.length() > MAX_RESPONSE_CHARS) {
            responseBody = responseBody.substring(0, MAX_RESPONSE_CHARS);
        }

        String callId = str(call.get("id"));

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String preview = responseBody != null && responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
            if (trace != null) trace.add(traceEntry(callId, callName, method, url, res.statusCode(), preview, rawResponseForTrace(responseBody)));
            throw new RuntimeException("Call '" + callName + "' returned HTTP " + res.statusCode() + ": " + preview);
        }

        List<Map<String, Object>> captureConfigs = listOf(call.get("responseCaptures"));
        Map<String, String> capturedThisCall = applyResponseCaptures(responseBody, captureConfigs, callName, warnings, rawCaptured);
        vars.putAll(capturedThisCall);

        // A real gap found live: a real (non-test) run that gets a 2xx response but whose captures
        // all match nothing left the admin with only "Capture 'X' matched nothing" per field and no
        // way to see what the API actually sent back — testRun captures the full raw response for
        // exactly this reason, but runSequence (trace == null here) never stored it anywhere at
        // all. Surface a preview of the real response through the same warnings -> item.lastError
        // path admins already see in the ticket UI, so a real-execution mismatch is diagnosable
        // without having to reproduce it in the wizard's Test step.
        if (warnings != null && trace == null && !captureConfigs.isEmpty() && capturedThisCall.size() < captureConfigs.size()) {
            String preview = responseBody != null && responseBody.length() > 1000 ? responseBody.substring(0, 1000) + "…" : responseBody;
            warnings.add("Real response received for call '" + callName + "': " + preview);
        }

        if (trace != null) {
            String preview = responseBody != null && responseBody.length() > 2000 ? responseBody.substring(0, 2000) + "…" : responseBody;
            trace.add(traceEntry(callId, callName, method, url, res.statusCode(), preview, rawResponseForTrace(responseBody)));
        }
    }

    /** The one real JsonPath-extraction step (Jayway JsonPath, {@code JsonPath.read}) — evaluates each configured {name,jsonPath} capture against a response body and returns name→value. Shared by the live call path above and {@link #evaluateResponseCaptures} below (re-evaluating an already-fetched response, no new call), so both ever have exactly one implementation of "how a capture resolves." */
    private Map<String, String> applyResponseCaptures(String responseBody, List<Map<String, Object>> responseCaptures, String callName, List<String> warnings) {
        return applyResponseCaptures(responseBody, responseCaptures, callName, warnings, null);
    }

    /** @param rawOut when non-null, also collects each capture's RAW (pre-stringification) JsonPath.read result — needed to humanize a captured JSON object/array into nodelist entries; see runSequence. */
    private Map<String, String> applyResponseCaptures(String responseBody, List<Map<String, Object>> responseCaptures, String callName, List<String> warnings, Map<String, Object> rawOut) {
        Map<String, String> captured = new LinkedHashMap<>();
        for (Map<String, Object> cap : responseCaptures) {
            String name = str(cap.get("name"));
            String jsonPath = str(cap.get("jsonPath"));
            if (name == null || jsonPath == null) continue;
            try {
                Object extracted = JsonPath.read(responseBody, jsonPath);
                if (rawOut != null) rawOut.put(name, extracted);
                captured.put(name, extracted != null ? String.valueOf(extracted) : "");
            } catch (Exception e) {
                log.warn("[ExternalApi] Capture '{}' (jsonPath {}) failed on call '{}': {}", name, jsonPath, callName, e.getMessage());
                if (warnings != null) {
                    warnings.add("Capture '" + name + "' (jsonPath " + jsonPath + ") on call '" + callName + "' matched nothing in the response");
                }
            }
        }
        return captured;
    }

    /**
     * "Verify Captures" — AI Workflow Builder's Response Mapping step. Re-evaluates each call's
     * (AI-proposed or admin-edited) responseCaptures against a response ALREADY fetched by an
     * earlier "Test this call now" run — no new live HTTP call is made. Exists specifically so the
     * wizard never has to invoke the real (possibly non-idempotent — e.g. "place an order") API a
     * second time just to confirm a JsonPath resolves; the admin already paid that cost once in
     * the Test step, and this endpoint replays that same captured data.
     */
    public WorkflowActionTestResult evaluateResponseCaptures(List<AiRefineCallInputDto> calls) {
        Map<String, String> vars = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> trace = new ArrayList<>();
        boolean anyCaptureConfigured = false;
        for (AiRefineCallInputDto c : calls) {
            if (c.getRawResponse() == null || c.getRawResponse().isBlank()) continue;
            List<Map<String, Object>> captures = c.getExistingResponseCaptures() != null ? c.getExistingResponseCaptures() : List.of();
            if (!captures.isEmpty()) anyCaptureConfigured = true;
            vars.putAll(applyResponseCaptures(c.getRawResponse(), captures, c.getName(), warnings));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("callId", c.getId());
            entry.put("name", c.getName());
            trace.add(entry);
        }
        // A real bug found live: this always returned success=true with the collected `warnings`
        // silently discarded, even when EVERY capture failed to match anything (empty
        // capturedValues) — the wizard's Step 5 read that as "verified fine" with no visible
        // failure reason, which is exactly how a set of JSONPaths that don't match the real
        // response shape (a genuinely wrong mapping, not a re-check bug) went unnoticed until it
        // ran for real on a ticket. Report it as an actual failure — with the same specific
        // per-field reasons runSequence's warnings already surface — rather than a quiet no-op.
        if (anyCaptureConfigured && vars.isEmpty() && !warnings.isEmpty()) {
            return WorkflowActionTestResult.failure(String.join("; ", warnings), trace);
        }
        WorkflowActionTestResult result = WorkflowActionTestResult.success(new LinkedHashMap<>(vars), trace);
        if (!warnings.isEmpty()) result.setError(String.join("; ", warnings));
        return result;
    }

    /** Bounded slice of the real response body for the new "Auto-map from this response" AI feature — distinct from the short human-facing responsePreview above, which stays as-is. */
    private String rawResponseForTrace(String responseBody) {
        if (responseBody == null) return "";
        return responseBody.length() > MAX_RAW_RESPONSE_FOR_TRACE_CHARS
                ? responseBody.substring(0, MAX_RAW_RESPONSE_FOR_TRACE_CHARS) : responseBody;
    }

    private Map<String, Object> traceEntry(String callId, String callName, String method, String url, int status, String responsePreview, String rawResponse) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("callId", callId);
        entry.put("name", callName);
        entry.put("request", method + " " + url);
        entry.put("status", status);
        entry.put("responsePreview", responsePreview);
        entry.put("rawResponse", rawResponse);
        return entry;
    }

    @SuppressWarnings("unchecked")
    /**
     * A real gap found live: auth.type="api_key" only ever placed the key in a header — some real
     * APIs (SerpAPI's Google Flights included) require it as a query parameter instead, and the
     * only workaround before this was a this.<key> request-mapping placeholder with no admin-facing
     * UI anywhere to ever set its value (see ExternalApiCallsEditor.tsx's ExternalApiAuth.location
     * comment), or hardcoding the real secret in plaintext directly in the urlTemplate. Runs after
     * the unresolved-placeholder check and before the URI is built, appending "?"/"&" + paramName +
     * "=" + the encrypted-at-rest token (URL-encoded the same way every other substituted value is).
     */
    private String appendAuthQueryParam(String url, Object authObj) {
        if (!(authObj instanceof Map)) return url;
        Map<String, Object> auth = (Map<String, Object>) authObj;
        if (!"api_key".equals(str(auth.get("type"))) || !"query".equals(str(auth.get("location")))) return url;
        String token = decryptOrNull(str(auth.get("tokenEnc")));
        if (token == null) return url;
        String paramName = Optional.ofNullable(str(auth.get("headerName"))).filter(s -> !s.isBlank()).orElse("api_key");
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + paramName + "=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void applyAuth(HttpRequest.Builder builder, Object authObj) {
        if (!(authObj instanceof Map)) return;
        Map<String, Object> auth = (Map<String, Object>) authObj;
        String type = str(auth.get("type"));
        if (type == null || "none".equals(type)) return;

        switch (type) {
            case "bearer" -> {
                String token = decryptOrNull(str(auth.get("tokenEnc")));
                if (token != null) builder.header("Authorization", "Bearer " + token);
            }
            case "api_key" -> {
                if ("query".equals(str(auth.get("location")))) return; // already placed in the URL by appendAuthQueryParam
                String token = decryptOrNull(str(auth.get("tokenEnc")));
                String headerName = Optional.ofNullable(str(auth.get("headerName"))).filter(s -> !s.isBlank()).orElse("X-API-Key");
                if (token != null) builder.header(headerName, token);
            }
            case "basic" -> {
                String user = decryptOrNull(str(auth.get("usernameEnc")));
                String pass = decryptOrNull(str(auth.get("passwordEnc")));
                if (user != null) {
                    String combo = user + ":" + (pass != null ? pass : "");
                    builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(combo.getBytes(StandardCharsets.UTF_8)));
                }
            }
            default -> log.warn("[ExternalApi] Unknown auth type '{}' — no auth applied", type);
        }
    }

    private String decryptOrNull(String enc) {
        if (enc == null || enc.isBlank()) return null;
        try {
            return aes.decrypt(enc);
        } catch (Exception e) {
            log.error("[ExternalApi] Failed to decrypt a configured credential: {}", e.getMessage());
            return null;
        }
    }

    private String substitute(String template, Map<String, String> vars) {
        if (template == null) return null;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * A real bug found live: plain {@link #substitute} does a raw string swap with no escaping at
     * all, which is fine for a JSON bodyTemplate (values there just need to not contain a literal
     * unescaped quote, an existing accepted gap) but breaks urlTemplate the moment a substituted
     * value contains a character that's illegal in a URI — e.g. a ticket field value with a space
     * ("Ben Gurion") turned "...?origin=Ben Gurion&..." into an invalid URI, thrown as "Illegal
     * character in query at index N" from `new URI(url)` — the template's own literal structure
     * (?, &, =) must stay intact, but each substituted VALUE needs percent-encoding. Every
     * urlTemplate placeholder is a substituted value (never literal template text), so this can
     * safely encode every replacement without needing to distinguish path vs. query position.
     */
    private String substituteForUrl(String template, Map<String, String> vars) {
        if (template == null) return null;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = vars.getOrDefault(m.group(1), "");
            String encoded = URLEncoder.encode(val, StandardCharsets.UTF_8);
            m.appendReplacement(sb, Matcher.quoteReplacement(encoded));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static final Set<String> TICKET_COLUMN_FIELDS = Set.of("title", "description", "status", "priority");

    private Object resolveInputField(TicketEntity ticket, WorkflowItemEntity item, String key) {
        if (key.startsWith("this.")) {
            if (item == null || item.getFieldValues() == null) return null;
            return item.getFieldValues().get(key.substring("this.".length()));
        }
        // "ticket." is optional here for backward compatibility — older saved configs (and the
        // frontend's existing ticket-field picker) store a bare key like "title" with no prefix at
        // all; newer AI drafts/pickers may send "ticket.title" for symmetry with the "this." case.
        String ticketKey = key.startsWith("ticket.") ? key.substring("ticket.".length()) : key;
        return switch (ticketKey) {
            case "title" -> ticket.getTitle();
            case "description" -> ticket.getDescription();
            case "status" -> ticket.getStatus();
            case "priority" -> ticket.getPriority();
            default -> ticket.getTicketData() != null ? ticket.getTicketData().get(ticketKey) : null;
        };
    }

    /** Returns true if it wrote to the ticket (caller must save); item-side writes are saved by the caller separately. */
    private boolean applyTarget(WorkflowItemEntity item, TicketEntity ticket, String target, String value) {
        if (target.startsWith("this.")) {
            String key = target.substring("this.".length());
            Map<String, Object> fv = item.getFieldValues();
            if (fv == null) { fv = new LinkedHashMap<>(); item.setFieldValues(fv); }
            fv.put(key, value);
            return false;
        }
        if (target.startsWith("ticket.")) {
            String key = target.substring("ticket.".length());
            if (TICKET_COLUMN_FIELDS.contains(key)) {
                switch (key) {
                    case "title" -> ticket.setTitle(value);
                    case "description" -> ticket.setDescription(value);
                    case "status" -> ticket.setStatus(value);
                    case "priority" -> ticket.setPriority(value);
                }
            } else {
                Map<String, Object> td = ticket.getTicketData();
                if (td == null) { td = new LinkedHashMap<>(); ticket.setTicketData(td); }
                td.put(key, value);
            }
            return true;
        }
        log.warn("[ExternalApi] Response mapping target '{}' doesn't start with 'ticket.' or 'this.' — ignored", target);
        return false;
    }

    /** field_key -> field_type for every field_definitions row of the given entity_type ("ticket" or "workflow") — looked up once per real run, not per mapping, since a sequence can have several response mappings. */
    private Map<String, String> fieldTypesByEntity(String entityType) {
        Map<String, String> types = new HashMap<>();
        for (FieldDefinitionsEntity f : fieldDefinitionsRepo.findByEntityTypeOrderByDisplayOrder(entityType)) {
            types.put(f.getFieldKey(), f.getFieldType());
        }
        return types;
    }

    /**
     * Turns a raw JsonPath.read() result into the list of readable text nodes to append to a
     * "nodelist" field: a JSON array becomes one humanized node per element (e.g. one node per
     * flight option); a single object/scalar becomes exactly one humanized node. Blank/null
     * produces no nodes at all (caller treats that as "nothing to add", same as any other capture
     * that resolved empty).
     */
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

    /** "take json break it into human readable data as string" — flattens a JSON object/array into a plain "key: value, key2: value2" line instead of a raw {@code Map.toString()}/JSON dump. Recurses so a nested object/array reads inline rather than as a dump of its own. */
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

    /** Appends (never overwrites) — "how to add node to the list": reads whatever is already stored under key (or starts a fresh list), adds every new node, writes the combined list back. */
    @SuppressWarnings("unchecked")
    private void appendNodes(Map<String, Object> data, String key, List<String> nodesToAdd) {
        List<Object> nodes = data.get(key) instanceof List<?> existing ? new ArrayList<>(existing) : new ArrayList<>();
        nodes.addAll(nodesToAdd);
        data.put(key, nodes);
    }

    /** nodelist counterpart to {@link #applyTarget} — same target grammar ("this."/"ticket."), append-only semantics instead of overwrite. Returns true if it wrote to the ticket (caller must save), matching applyTarget's contract. */
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
        log.warn("[ExternalApi] Response mapping target '{}' doesn't start with 'ticket.' or 'this.' — ignored", target);
        return false;
    }

    private void fail(WorkflowItemEntity item, String message) {
        item.setStatus("blocked");
        item.setLastError(message);
        itemRepo.save(item);
        adminInboxService.createIfAbsent(item.getTicketId(), "wf_extapi_fail_" + item.getId(), "WORKFLOW_ACTION_FAILED",
                "Action item failed: " + item.getTitle() + " [TT-" + item.getTicketId() + "] — " + message);
        log.error("[ExternalApi] Item {} blocked: {}", item.getId(), message);
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
