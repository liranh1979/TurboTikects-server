package com.turbotikects.turbotikectsserver.services;

import com.jayway.jsonpath.JsonPath;
import com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.WorkflowItemRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.URI;
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
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private final WorkflowItemRepository itemRepo;
    private final TicketRepository ticketRepo;
    private final AesEncryptionUtils aes;
    private final AdminInboxService adminInboxService;
    private final WorkflowService workflowService;

    // @Lazy on WorkflowService only — mirrors the WorkflowService<->ApprovalService cycle fix
    // (WorkflowService.activateItem dispatches INTO this class, this class calls back into
    // WorkflowService.onItemCompleted when the sequence finishes).
    public ExternalApiActionExecutor(WorkflowItemRepository itemRepo, TicketRepository ticketRepo,
                                      AesEncryptionUtils aes, AdminInboxService adminInboxService,
                                      @Lazy WorkflowService workflowService) {
        this.itemRepo = itemRepo;
        this.ticketRepo = ticketRepo;
        this.aes = aes;
        this.adminInboxService = adminInboxService;
        this.workflowService = workflowService;
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
                runCall(client, call, vars, trace);
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

            for (Map<String, Object> call : calls) {
                runCall(client, call, vars, null);
            }

            boolean ticketChanged = false;
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
            log.info("[ExternalApi] Item {} completed successfully ({} calls)", itemId, calls.size());
            workflowService.onItemCompleted(itemId, item.getTicketId());
        } catch (Exception e) {
            log.error("[ExternalApi] Item {} failed: {}", itemId, e.getMessage(), e);
            WorkflowItemEntity fresh = itemRepo.findById(itemId).orElse(null);
            if (fresh != null) fail(fresh, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /** trace is non-null only in {@link #testRun} — appended to for the "test this call now" result panel, ignored (null) during real item execution. */
    private void runCall(HttpClient client, Map<String, Object> call, Map<String, String> vars, List<Map<String, Object>> trace) throws Exception {
        String callName = Optional.ofNullable(str(call.get("name"))).orElse("call");
        String method = Optional.ofNullable(str(call.get("method"))).orElse("GET").toUpperCase();
        String url = substitute(str(call.get("urlTemplate")), vars);
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Call '" + callName + "' has no URL configured");

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

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String preview = responseBody != null && responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
            if (trace != null) trace.add(traceEntry(callName, method, url, res.statusCode(), preview));
            throw new RuntimeException("Call '" + callName + "' returned HTTP " + res.statusCode() + ": " + preview);
        }

        for (Map<String, Object> cap : listOf(call.get("responseCaptures"))) {
            String name = str(cap.get("name"));
            String jsonPath = str(cap.get("jsonPath"));
            if (name == null || jsonPath == null) continue;
            try {
                Object extracted = JsonPath.read(responseBody, jsonPath);
                vars.put(name, extracted != null ? String.valueOf(extracted) : "");
            } catch (Exception e) {
                log.warn("[ExternalApi] Capture '{}' (jsonPath {}) failed on call '{}': {}", name, jsonPath, callName, e.getMessage());
            }
        }

        if (trace != null) {
            String preview = responseBody != null && responseBody.length() > 2000 ? responseBody.substring(0, 2000) + "…" : responseBody;
            trace.add(traceEntry(callName, method, url, res.statusCode(), preview));
        }
    }

    private Map<String, Object> traceEntry(String callName, String method, String url, int status, String responsePreview) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", callName);
        entry.put("request", method + " " + url);
        entry.put("status", status);
        entry.put("responsePreview", responsePreview);
        return entry;
    }

    @SuppressWarnings("unchecked")
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
