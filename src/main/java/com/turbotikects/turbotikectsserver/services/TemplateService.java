package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import com.turbotikects.turbotikectsserver.repositorys.TemplateRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TemplateService {

    private final TemplateRepository templateRepo;
    private final TemplateVersionRepository versionRepo;
    private final FieldDefinitionsService fieldDefinitionsService;
    private final AiSettingsService aiSettingsService;
    private final AesEncryptionUtils aes;
    private final AiChatService aiChatService;
    private final com.turbotikects.turbotikectsserver.repositorys.ActionItemLibraryRepository actionItemLibraryRepo;

    public TemplateService(TemplateRepository templateRepo,
                           TemplateVersionRepository versionRepo,
                           FieldDefinitionsService fieldDefinitionsService,
                           AiSettingsService aiSettingsService,
                           AesEncryptionUtils aes,
                           AiChatService aiChatService,
                           com.turbotikects.turbotikectsserver.repositorys.ActionItemLibraryRepository actionItemLibraryRepo) {
        this.templateRepo = templateRepo;
        this.versionRepo = versionRepo;
        this.fieldDefinitionsService = fieldDefinitionsService;
        this.aiSettingsService = aiSettingsService;
        this.aes = aes;
        this.aiChatService = aiChatService;
        this.actionItemLibraryRepo = actionItemLibraryRepo;
    }

    public List<TemplateSummaryDto> getAll() {
        return getAll(true);
    }

    public List<TemplateSummaryDto> getAll(boolean isManager) {
        List<TemplateEntity> templates = templateRepo.findAll();
        List<TemplateSummaryDto> result = new ArrayList<>();
        for (TemplateEntity t : templates) {
            // Problem Management: internal-only templates never reach a non-manager's New
            // Ticket picker — see V2/Problem Management/04-permissions-end-users.html.
            if (t.isInternal() && !isManager) continue;
            TemplateSummaryDto dto = new TemplateSummaryDto();
            dto.setId(t.getId());
            dto.setName(t.getName());
            dto.setDescription(t.getDescription());
            dto.setCreatedAt(t.getCreatedAt());
            dto.setUpdatedAt(t.getUpdatedAt());
            versionRepo.findByTemplateIdAndIsCurrentTrue(t.getId())
                    .ifPresent(v -> dto.setCurrentVersionNumber(v.getVersionNumber()));
            dto.setDefault(t.isDefault());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public TemplateWithLayoutDto create(SaveLayoutRequestDto dto) {
        TemplateEntity template = new TemplateEntity();
        template.setName(dto.getName() != null ? dto.getName() : "New Template");
        template.setDescription(dto.getDescription());
        template.setAiPurpose(dto.getAiPurpose());
        template = templateRepo.save(template);

        Map<String, Object> layout = dto.getLayout() != null ? dto.getLayout() : buildDefaultLayout();
        encryptWorkflowSecrets(layout);

        TemplateVersionEntity version = new TemplateVersionEntity();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setLayout(layout);
        version.setCurrent(true);
        version = versionRepo.save(version);

        return toWithLayoutDto(template, version);
    }

    public TemplateWithLayoutDto getWithLayout(Long id) {
        TemplateEntity template = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        TemplateVersionEntity version = versionRepo.findByTemplateIdAndIsCurrentTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toWithLayoutDto(template, version);
    }

    @Transactional
    public TemplateWithLayoutDto saveLayout(Long id, SaveLayoutRequestDto dto) {
        TemplateEntity template = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (dto.getName() != null) template.setName(dto.getName());
        if (dto.getDescription() != null) template.setDescription(dto.getDescription());
        if (dto.getAiPurpose() != null) template.setAiPurpose(dto.getAiPurpose());
        template = templateRepo.save(template);

        TemplateVersionEntity current = versionRepo.findByTemplateIdAndIsCurrentTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        current.setCurrent(false);
        versionRepo.save(current);

        Map<String, Object> newLayout = dto.getLayout() != null ? dto.getLayout() : current.getLayout();
        if (dto.getLayout() != null) {
            // The client never receives plaintext or ciphertext secrets back (see
            // maskWorkflowSecrets/toWithLayoutDto) — only a "hasToken"-style flag. So an auth block
            // with no "token"/"username"/"password" key at all means "admin didn't touch this,"
            // and the previously-encrypted value must be carried forward or it would be silently
            // wiped on every unrelated template edit. A present (even blank, for "clear") key means
            // an explicit admin action and always wins.
            carryForwardWorkflowSecrets(newLayout, current.getLayout());
            carryForwardSecretsFromLibrary(newLayout);
            encryptWorkflowSecrets(newLayout);
        }

        TemplateVersionEntity newVersion = new TemplateVersionEntity();
        newVersion.setTemplateId(id);
        newVersion.setVersionNumber(current.getVersionNumber() + 1);
        newVersion.setLayout(newLayout);
        newVersion.setCurrent(true);
        newVersion = versionRepo.save(newVersion);

        return toWithLayoutDto(template, newVersion);
    }

    @Transactional
    public TemplateSummaryDto setDefault(Long id) {
        TemplateEntity template = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        templateRepo.clearAllDefaults();
        template.setDefault(true);
        template = templateRepo.save(template);
        TemplateSummaryDto dto = new TemplateSummaryDto();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setDefault(true);
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        versionRepo.findByTemplateIdAndIsCurrentTrue(id)
                .ifPresent(v -> dto.setCurrentVersionNumber(v.getVersionNumber()));
        return dto;
    }

    public void delete(Long id) {
        if (!templateRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        templateRepo.deleteById(id);
    }

    public Map<String, Object> aiSuggestLayout(Long id, AiSuggestLayoutRequestDto dto)
            throws IOException, URISyntaxException, InterruptedException {

        if (!templateRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        List<FieldDefinitionsEntity> fields = fieldDefinitionsService.getCustomFields("ticket");
        Map<String, String> enTranslations = fieldDefinitionsService.getFieldTranslations("en", "ticket_fields");

        StringBuilder fieldList = new StringBuilder();
        for (FieldDefinitionsEntity f : fields) {
            String label = enTranslations.getOrDefault(f.getFieldKey(), f.getFieldKey());
            fieldList.append("- fieldKey: ").append(f.getFieldKey())
                    .append(", fieldType: ").append(f.getFieldType())
                    .append(", label: ").append(label)
                    .append(", isSystem: ").append(f.isSystem())
                    .append("\n");
        }

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("You are a ticket template designer. Given a list of available fields, " +
                "return a JSON array selecting and ordering fields that best match the admin's requirement. " +
                "Always include all system fields (isSystem: true). " +
                "Use only fieldKeys from the provided list. " +
                "Return ONLY a valid JSON array, no markdown, no explanation.");

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Available fields:\n" + fieldList +
                "\nAdmin requirement: " + dto.getPrompt() +
                "\n\nReturn a JSON array of objects with this shape: " +
                "[{\"fieldKey\": \"...\", \"fieldType\": \"...\", \"isSystem\": true/false, " +
                "\"displayOrder\": N, \"defaultValue\": \"\", \"width\": \"full\"}]");

        String raw = aiSettingsService.sendLlmRequest(aiSettings, List.of(system, user));
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> suggestedFields = mapper.readValue(cleaned, new TypeReference<>() {});

        // Wrap in tabbed layout structure
        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("tabKey", "main");
        tab.put("label", "Main");
        tab.put("fields", suggestedFields);

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("tabs", List.of(tab));
        return layout;
    }

    /** Serializes the available workflow fields (key + type) as a JSON array for the prompt, mirroring LdapMappingService's systemFieldsJson approach so the AI can reason about type fit, not just name. */
    private String workflowFieldsJson(List<WorkflowFieldRefDto> workflowFields) {
        if (workflowFields == null || workflowFields.isEmpty()) return "(none configured)";
        List<Map<String, String>> asMaps = workflowFields.stream()
                .map(f -> Map.of("key", f.getKey() == null ? "" : f.getKey(), "type", f.getType() == null ? "text" : f.getType()))
                .toList();
        try {
            return new ObjectMapper().writeValueAsString(asMaps);
        } catch (Exception e) {
            return "(none configured)";
        }
    }

    private Set<String> existingWorkflowFieldKeys(List<WorkflowFieldRefDto> workflowFields) {
        if (workflowFields == null) return Set.of();
        Set<String> keys = new HashSet<>();
        for (WorkflowFieldRefDto f : workflowFields) {
            if (f.getKey() != null) keys.add(f.getKey());
        }
        return keys;
    }

    /**
     * Sanitizes an AI-drafted "missingWorkflowFields" suggestion list in place, deliberately
     * hardened over LdapMappingService's equivalent (which passes the LLM's suggestedFieldType
     * straight through with no validation): drops entries with no usable key or that duplicate an
     * already-existing workflow field, de-dupes repeated suggestions, and whitelists
     * suggestedFieldType to the same text/number/date/checkbox set SimpleItemFieldsEditor's
     * RENDERABLE_TYPES already uses for the identical "fillable workflow field" concept — anything
     * else (or missing) defaults to "text" rather than being trusted.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sanitizeMissingWorkflowFields(Map<String, Object> draft, Set<String> existingKeys) {
        Set<String> validFieldTypes = Set.of("text", "number", "date", "checkbox");
        List<Map<String, Object>> missing = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        if (draft.get("missingWorkflowFields") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> s = new LinkedHashMap<>((Map<String, Object>) o);
                String key = s.get("suggestedFieldKey") instanceof String k ? k.trim() : null;
                if (key == null || key.isBlank()) continue;
                if (existingKeys.contains(key)) continue;
                if (!seenKeys.add(key)) continue;
                if (!(s.get("suggestedFieldType") instanceof String t) || !validFieldTypes.contains(t)) {
                    s.put("suggestedFieldType", "text");
                }
                s.putIfAbsent("suggestedLabel", key);
                s.put("suggestedFieldKey", key);
                missing.add(s);
            }
        }
        return missing;
    }

    /** Strips any real credential the model might have echoed back and normalizes auth.type/headers/bodyTemplate — shared by every "produce/fix a call" AI method. Does NOT touch id/order/responseCaptures — callers decide those. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeCall(Map<String, Object> rawCall) {
        Set<String> validAuthTypes = Set.of("none", "bearer", "api_key", "basic");
        Map<String, Object> call = new LinkedHashMap<>(rawCall);
        call.putIfAbsent("headers", new ArrayList<>());
        call.putIfAbsent("bodyTemplate", "");

        Map<String, Object> auth = call.get("auth") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) call.get("auth")) : new LinkedHashMap<>();
        auth.remove("token"); auth.remove("username"); auth.remove("password");
        auth.remove("tokenEnc"); auth.remove("usernameEnc"); auth.remove("passwordEnc");
        if (!(auth.get("type") instanceof String s) || !validAuthTypes.contains(s)) {
            auth.put("type", "none");
        }
        call.put("auth", auth);
        return call;
    }

    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern FIELD_REF_PLACEHOLDER = Pattern.compile("^(?:ticket|this)\\.[A-Za-z0-9_]+$");

    /**
     * A real bug found live: system prompts explicitly require bare "letters/numbers/underscores
     * only" placeholder names (e.g. "{{origin}}"), but a model given exact "ticket.<field>"/
     * "this.<field>" strings sometimes echoes them verbatim into "{{...}}" instead of inventing a
     * bare name — ExternalApiActionExecutor's substitution only ever recognizes "[a-zA-Z0-9_]+"
     * inside "{{ }}", so that placeholder is silently left unresolved and the call fails loudly at
     * test/run time with a confusing "unresolved placeholder" error the admin has no obvious way to
     * fix. Since the offending "{{ticket.foo}}"/"{{this.foo}}" text IS already a valid ticketField
     * reference, repair it automatically: rename the placeholder to a safe identifier (dots ->
     * underscores) everywhere it's used in this call's templates, and add the matching
     * fieldMappings.request entry if the model forgot to.
     */
    @SuppressWarnings("unchecked")
    private void repairCallPlaceholders(Map<String, Object> call, Set<String> mappedPlaceholders, List<Object> request) {
        if (call.get("urlTemplate") instanceof String s) {
            call.put("urlTemplate", repairTemplateString(s, mappedPlaceholders, request));
        }
        if (call.get("bodyTemplate") instanceof String s) {
            call.put("bodyTemplate", repairTemplateString(s, mappedPlaceholders, request));
        }
        if (call.get("headers") instanceof List<?> headers) {
            for (Object h : headers) {
                if (h instanceof Map<?, ?> hmRaw && hmRaw.get("valueTemplate") instanceof String vt) {
                    ((Map<String, Object>) hmRaw).put("valueTemplate",
                            repairTemplateString(vt, mappedPlaceholders, request));
                }
            }
        }
    }

    /** Whitelists a fix/draft's fieldMappings.request entries the same way sanitizeMatchFieldsDraft does for matchedFields — drops any entry whose ticketField isn't a real "ticket.<key>" (from the provided ticket fields) or "this.<key>" (from the provided/existing workflow fields), and de-dupes by placeholder name (last one wins). */
    private List<Object> sanitizeRequestMappings(List<Object> rawMappings, List<WorkflowFieldRefDto> ticketFields, Set<String> existingWorkflowFieldKeys) {
        Set<String> validTicketKeys = new HashSet<>();
        if (ticketFields != null) {
            for (WorkflowFieldRefDto f : ticketFields) {
                if (f.getKey() != null) validTicketKeys.add(f.getKey());
            }
        }
        Map<String, Object> byPlaceholder = new LinkedHashMap<>();
        for (Object o : rawMappings) {
            if (!(o instanceof Map<?, ?> m)) continue;
            if (!(m.get("placeholder") instanceof String placeholder) || placeholder.isBlank()) continue;
            if (!(m.get("ticketField") instanceof String field)) continue;
            boolean valid = field.startsWith("ticket.")
                    ? validTicketKeys.contains(field.substring("ticket.".length()))
                    : field.startsWith("this.") && existingWorkflowFieldKeys.contains(field.substring("this.".length()));
            if (!valid) continue;
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("placeholder", placeholder);
            clean.put("ticketField", field);
            byPlaceholder.put(placeholder, clean);
        }
        return new ArrayList<>(byPlaceholder.values());
    }

    /**
     * "Fix/adjust with AI" — external_api Field Mapping and Test steps of the guided wizard flow.
     * Either a real test just failed (a bad URL, a wrong endpoint, an unresolved placeholder, ...)
     * or the admin simply wants to change something about the call — either way, rather than
     * hand-edit it, they ask the AI to adjust it. Continues the SAME persisted, replayed-history
     * "api_action_builder" AiChatSession the wizard's earlier steps (aiDiscoverEndpoints/
     * aiDraftCallSkeleton/aiMapCallFields) already used — so the AI still has the original
     * documentation/intent/field-mapping context, not just this one call in isolation. The call's
     * own id/order/responseCaptures are always preserved verbatim (never regenerated), since the
     * frontend correlates test traces and response-mapping progress by call id.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> aiFixCall(AiFixCallRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getCall() == null || dto.getCall().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "call is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            sessionId = aiChatService.createSession(userId, "api_action_builder", null).getId();
        }
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        ObjectMapper mapper = new ObjectMapper();
        String callJson;
        String requestMappingsJson;
        try {
            callJson = mapper.writeValueAsString(dto.getCall());
            requestMappingsJson = mapper.writeValueAsString(dto.getRequestMappings() == null ? List.of() : dto.getRequestMappings());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid call payload");
        }

        String ticketFieldList = workflowFieldsJson(dto.getTicketFields());
        String workflowFieldList = workflowFieldsJson(dto.getWorkflowFields());

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                A single HTTP API call in a workflow automation system just failed a real test run. \
                Fix ONLY what's broken — method, urlTemplate, headers, auth, or bodyTemplate — using \
                the documentation, the admin's instructions, and the error to figure out what's \
                actually wrong (a wrong endpoint path, a wrong query/body param name, a missing \
                required param, an auth mistake, etc.). Produce a JSON object with EXACTLY this shape \
                (no markdown, no explanation, ONLY the JSON):
                {
                  "call": {
                    "name": "short_snake_case_name",
                    "method": "GET"|"POST"|"PUT"|"PATCH"|"DELETE",
                    "urlTemplate": "https://... using {{placeholder}} for variable parts",
                    "headers": [{"key": "Header-Name", "valueTemplate": "value or {{placeholder}}"}],
                    "auth": {"type": "none"|"bearer"|"api_key"|"basic", "headerName": "only for api_key type"},
                    "bodyTemplate": "JSON string body using {{placeholder}} for variable parts, or empty string for no body"
                  },
                  "fieldMappings": {
                    "request": [{"placeholder": "name used in templates above", "ticketField": "ticket.<field> or this.<workflow field>"}]
                  }
                }
                Rules:
                - Do not change what the call is for — keep doing the same thing the original call was
                  for, just fix what's actually broken.
                - NEVER include a real token/username/password value — the admin enters real
                  credentials separately. Only set "auth.type" (and "headerName" for api_key).
                - Placeholder names MUST contain ONLY lowercase letters, numbers, and underscores — no
                  dots, dashes, or spaces. This applies everywhere a name is used: {{placeholder}} in
                  templates and fieldMappings.request[].placeholder.
                - "fieldMappings.request" must be the COMPLETE, corrected list for this call — reuse
                  the existing mappings that are still correct, drop any that no longer apply, and add
                  any new ones the fix needs. Every {{placeholder}} you reference in the call MUST
                  have a matching entry here.
                - fieldMappings.request[].ticketField MUST start with either "ticket." (from the
                  provided ticket fields) or "this." (from the provided workflow fields) — never a
                  bare name.
                - If earlier turns are present in this conversation, you already know the original
                  intent and field matches from them — use that context, don't ask for it again.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Available ticket fields: " + ticketFieldList +
                "\n\nAvailable workflow fields: " + workflowFieldList +
                "\n\nWhat this action should do: " + (dto.getIntent() == null ? "" : dto.getIntent()) +
                "\n\nAPI documentation:\n" + (dto.getDocumentation() == null ? "" : dto.getDocumentation()) +
                "\n\nThe call as it currently is:\n" + callJson +
                "\n\nIts current request field mappings:\n" + requestMappingsJson +
                "\n\nWhat failed when tested:\n" + (dto.getError() == null ? "" : dto.getError()) +
                "\n\nAdmin's instructions for the fix: " + (dto.getInstructions() == null ? "" : dto.getInstructions()));

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(history);
        llmRequest.add(user);

        String raw = aiSettingsService.sendLlmRequest(aiSettings, llmRequest);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> result;
        try {
            result = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[TemplateService] aiFixCall: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a more specific instruction, or a different AI provider/model.");
        }

        Map<String, Object> fixedCall = sanitizeCall(result.get("call") instanceof Map
                ? (Map<String, Object>) result.get("call") : new LinkedHashMap<>());
        // The input side (method/urlTemplate/headers/auth/bodyTemplate) is the AI's job — id/order/
        // responseCaptures are not, the frontend correlates test traces and response-mapping progress
        // by the original call id.
        fixedCall.put("id", dto.getCall().get("id"));
        fixedCall.put("order", dto.getCall().get("order"));
        fixedCall.put("responseCaptures", dto.getCall().getOrDefault("responseCaptures", new ArrayList<>()));

        List<Object> rawRequestMappings = new ArrayList<>();
        if (result.get("fieldMappings") instanceof Map<?, ?> fm && fm.get("request") instanceof List<?> reqList) {
            rawRequestMappings.addAll(reqList);
        }
        List<Object> requestMappings = sanitizeRequestMappings(
                rawRequestMappings, dto.getTicketFields(), existingWorkflowFieldKeys(dto.getWorkflowFields()));

        Set<String> mappedPlaceholders = new HashSet<>();
        for (Object o : requestMappings) {
            if (o instanceof Map<?, ?> m && m.get("placeholder") instanceof String p) mappedPlaceholders.add(p);
        }
        repairCallPlaceholders(fixedCall, mappedPlaceholders, requestMappings);

        aiChatService.appendMessage(sessionId, "user", user.getContent());
        aiChatService.appendMessage(sessionId, "assistant", cleaned);

        Map<String, Object> draftOut = new LinkedHashMap<>();
        draftOut.put("call", fixedCall);
        Map<String, Object> fmOut = new LinkedHashMap<>();
        fmOut.put("request", requestMappings);
        draftOut.put("fieldMappings", fmOut);
        draftOut.put("sessionId", sessionId);
        return draftOut;
    }

    /** Renames any "{{ticket.<key>}}"/"{{this.<key>}}" placeholder in template to a safe identifier, adding a fieldMappings.request entry for it (mutating mappedPlaceholders/request) the first time each one is seen — valid bare placeholders are left untouched. */
    private String repairTemplateString(String template, Set<String> mappedPlaceholders, List<Object> request) {
        Matcher m = ANY_PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String raw = m.group(1);
            String replacement = raw;
            if (!raw.matches("[A-Za-z0-9_]+") && FIELD_REF_PLACEHOLDER.matcher(raw).matches()) {
                String safeName = raw.replace('.', '_');
                if (!mappedPlaceholders.contains(safeName)) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("placeholder", safeName);
                    entry.put("ticketField", raw);
                    request.add(entry);
                    mappedPlaceholders.add(safeName);
                }
                replacement = safeName;
            }
            result.append(template, last, m.start()).append("{{").append(replacement).append("}}");
            last = m.end();
        }
        result.append(template.substring(last));
        return result.toString();
    }

    /**
     * FEAT-06 Phase 7 — AI Workflow Builder extended to MCP. Unlike the external_api guided flow
     * (which has to infer an HTTP request shape from prose documentation), this is given the SERVER'S REAL
     * discovered tools — actual names and JSON-schema argument definitions from
     * McpController.discoverTools, not guessed — so the draft should be materially more reliable:
     * the model only has to pick from and map onto ground truth, not invent a plausible-looking
     * request shape. Same "AI proposes, human confirms" design as every other AI-assisted feature
     * here — the draft is never auto-applied, only returned for review in the same
     * McpToolCallsEditor/McpResponseMappingsEditor Phase 6 already built.
     */
    public Map<String, Object> aiSuggestMcpAction(AiMcpActionDraftRequestDto dto)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getTools() == null || dto.getTools().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tools is required — discover the server's tools first");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        ObjectMapper mapper = new ObjectMapper();
        String toolsJson;
        try {
            toolsJson = mapper.writeValueAsString(dto.getTools());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tools payload");
        }
        String fieldList = workflowFieldsJson(dto.getTicketFields());
        String workflowFieldList = workflowFieldsJson(dto.getWorkflowFields());

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You design the INPUT side of a call to an MCP (Model Context Protocol) server's tool \
                for a workflow automation system — which tool, and which fields feed its arguments. \
                You are given the server's REAL tools with their exact JSON-schema argument \
                definitions — only use tool names and argument names that actually appear in that \
                list, never invent one. Produce a JSON object with EXACTLY this shape (no markdown, \
                no explanation, ONLY the JSON):
                {
                  "calls": [
                    {
                      "toolName": "must be one of the provided tools' exact name",
                      "argumentMappings": [
                        {"toolArgument": "must be a property key in that tool's inputSchema.properties", "ticketField": "ticket.<field> or this.<workflow field>"}
                      ]
                    }
                  ],
                  "missingWorkflowFields": [
                    {"suggestedFieldKey": "snake_case_key", "suggestedLabel": "Human Label", "suggestedFieldType": "text"|"number"|"date"|"checkbox"}
                  ]
                }
                Do NOT attempt to describe or guess the response shape — no real response has been \
                captured yet at this point, so there is nothing to ground a guess in. Response field \
                mapping happens later, after a real test call, grounded in the real response.
                Rules:
                - Only map arguments that appear in the chosen tool's inputSchema.properties; prefer
                  mapping every property listed in that schema's "required" array.
                - argumentMappings[].ticketField reads from "ticket.<field>", one of the available
                  ticket fields (each given with its "type"), or "this.<workflow field>", one of the
                  available workflow fields (each given with its "type").
                - MANDATORY CHECK for every single "this.<key>" you write, with no exceptions: does
                  <key> EXACTLY match a key already in the provided workflow fields list, AND is
                  that field's "type" a genuinely good fit for what you're reading here? If YES, use
                  that exact key. If NO — including when nothing in the list is close, or a name-only
                  match has the wrong type — you MUST do BOTH of the following, never just the
                  "this.<key>" reference alone: (1) still write "this.<key>" at that location (invent
                  a new snake_case key if needed), AND (2) add exactly one entry for that same key to
                  the top-level "missingWorkflowFields" array. It is a mistake to reference a
                  "this.<key>" that is neither in the provided workflow fields list NOR declared in
                  "missingWorkflowFields" — every single one must be one or the other, no silent
                  third option. "suggestedFieldType" MUST be exactly one of "text", "number", "date",
                  or "checkbox" — never anything else.
                - A "this.<key>" reference used here means a value expected to already be known —
                  filled in by a human, or captured by an earlier call — before this call runs; if
                  you invent a new key here, it becomes a fillable input the admin can expose on the
                  item afterward.
                - If the admin's intent needs more than one tool call in sequence, create multiple
                  calls in order.
                - Keep it minimal and correct rather than speculative — if no tool clearly matches the
                  intent, pick the closest single reasonable one rather than fabricating multiple.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Available ticket fields: " + fieldList +
                "\n\nAvailable workflow fields: " + workflowFieldList +
                "\n\nWhat this action should do: " + (dto.getIntent() == null ? "" : dto.getIntent()) +
                "\n\nServer's real tools (JSON):\n" + toolsJson);

        String raw = aiSettingsService.sendLlmRequest(aiSettings, List.of(system, user));
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> draft;
        try {
            draft = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            // Same defense as the other AI draft methods here — a large/unfamiliar real tool schema
            // (e.g. a production MCP server with many complex tools) can make a smaller model respond
            // conversationally instead of with pure JSON; surface a clear 4xx, not a raw 500.
            log.warn("[TemplateService] aiSuggestMcpAction: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — it may not have understood the request or the tool schema was too complex for it. Try a shorter/clearer intent, a smaller set of tools, or a different AI provider/model.");
        }
        sanitizeMcpDraft(draft, existingWorkflowFieldKeys(dto.getWorkflowFields()));
        return draft;
    }

    /**
     * Defense in depth, mirrors sanitizeDraft: never trusts the LLM for stable/unique call
     * ids/ordering, guarantees calls/fieldMappings.response are always present arrays, and forces
     * "responseCaptures"/"fieldMappings.response" empty regardless of model output (see sanitizeDraft's
     * javadoc — same input-only step-1 reasoning applies here). mcp_tool calls have no per-call auth
     * to strip (the server connection's single auth lives at the node level, entered by the admin
     * directly — never asked of the LLM at all here).
     */
    @SuppressWarnings("unchecked")
    private void sanitizeMcpDraft(Map<String, Object> draft, Set<String> existingWorkflowFieldKeys) {
        List<Map<String, Object>> calls = new ArrayList<>();
        if (draft.get("calls") instanceof List<?> list) {
            int order = 0;
            for (Object c : list) {
                if (!(c instanceof Map)) continue;
                Map<String, Object> call = new LinkedHashMap<>((Map<String, Object>) c);
                call.put("id", UUID.randomUUID().toString());
                call.put("order", order++);
                call.putIfAbsent("argumentMappings", new ArrayList<>());
                call.put("responseCaptures", new ArrayList<>());
                calls.add(call);
            }
        }
        draft.put("calls", calls);

        Map<String, Object> fm = draft.get("fieldMappings") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) draft.get("fieldMappings")) : new LinkedHashMap<>();
        fm.put("response", new ArrayList<>());
        draft.put("fieldMappings", fm);

        draft.put("missingWorkflowFields", sanitizeMissingWorkflowFields(draft, existingWorkflowFieldKeys));
    }

    private static final int MAX_FETCHED_DOC_CHARS = 200_000;

    /**
     * Step 1 helper (external_api) — fetches an admin-given documentation URL's raw content
     * server-side (avoids a browser CORS block the frontend would otherwise hit) so it can be
     * reviewed/edited in the Documentation textarea before Discover Endpoints runs. The model is
     * expected to read the remaining markup directly — this only strips {@code <script>}/
     * {@code <style>} blocks, pure noise for documentation purposes that would otherwise bloat the
     * prompt for no benefit, regardless of whether the active model handles raw HTML well.
     * Deliberately more defensive than this codebase's existing outbound-fetch code (MCP tool
     * discovery, external_api live test calls) — neither of those validates the target host at
     * all, but this endpoint's whole point is "fetch whatever URL an admin pastes in," which is a
     * more direct SSRF vector than either of those (both point at a URL the admin is configuring
     * a specific integration against), so it additionally rejects loopback/private/link-local
     * targets.
     */
    public Map<String, Object> fetchDocumentationUrl(FetchDocumentationUrlRequestDto dto)
            throws IOException, InterruptedException {
        if (dto.getUrl() == null || dto.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }
        URI uri;
        try {
            uri = new URI(dto.getUrl().trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http/https URLs are supported");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This URL points to a private/internal address and can't be fetched");
                }
            }
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve this host");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "TurboTikects-DocFetcher/1.0")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not fetch this URL: " + e.getMessage());
        }
        if (response.statusCode() >= 400) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The URL returned HTTP " + response.statusCode());
        }

        String body = response.body() != null ? response.body() : "";
        String cleaned = body
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "");
        if (cleaned.length() > MAX_FETCHED_DOC_CHARS) {
            log.warn("[TemplateService] fetchDocumentationUrl: content from {} truncated from {} to {} chars",
                    host, cleaned.length(), MAX_FETCHED_DOC_CHARS);
            cleaned = cleaned.substring(0, MAX_FETCHED_DOC_CHARS);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", cleaned);
        return result;
    }

    private static final int MAX_DISCOVERED_ENDPOINTS = 25;
    private static final int MAX_DOC_EXCERPT_CHARS = 4000;
    private static final Set<String> VALID_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /**
     * Step 1 of the external_api wizard's guided AI flow — given raw pasted documentation that may
     * describe one or many distinct HTTP endpoints, enumerates every genuinely distinct one, each
     * with a verbatim excerpt of just its own portion of the docs (Step 2 only re-reads this
     * excerpt, not the whole document, so a paraphrase here would lose exact param/header names).
     * Continues the same persisted "api_action_builder" session every later step in this wizard
     * visit also uses (see aiDraftCallSkeleton/aiMapCallFields below), via the same
     * create-if-absent/replay-history/append-after pattern aiFixCall already established.
     */
    public Map<String, Object> aiDiscoverEndpoints(AiDiscoverEndpointsRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getDocumentation() == null || dto.getDocumentation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentation is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            sessionId = aiChatService.createSession(userId, "api_action_builder", null).getId();
        }
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You are given raw API documentation that may describe ONE or MANY distinct HTTP \
                endpoints. Identify every genuinely distinct endpoint it actually describes. Produce \
                a JSON object with EXACTLY this shape (no markdown, no explanation, ONLY the JSON):
                {
                  "endpoints": [
                    {
                      "id": "short_snake_case_slug, unique within this response",
                      "method": "GET"|"POST"|"PUT"|"PATCH"|"DELETE",
                      "title": "short human label, e.g. 'List Users'",
                      "summary": "one-line description of what this endpoint does",
                      "recommended": true|false,
                      "docExcerpt": "the verbatim slice of the ORIGINAL documentation describing this endpoint — method, path, params, example — copy real text, never paraphrase"
                    }
                  ]
                }
                Rules:
                - Only include endpoints the documentation ACTUALLY describes — never invent one.
                - If the docs describe only one endpoint, return a single-element array; never split
                  one endpoint into several, or merge two distinct ones into one.
                - "recommended" is true only for the endpoint(s) that best match "What this action
                  should do" below, if given — this NEVER filters or hides the other endpoints, it
                  only helps the admin prioritize which to look at first.
                - docExcerpt MUST be copied text, not a summary — a later step re-reads ONLY this
                  excerpt, not the whole document, so paraphrasing loses exact param/header names and
                  example formats.
                - If earlier turns are present in this conversation, treat this as a continuation —
                  if newer documentation text supersedes or extends what was discussed before, prefer
                  the newest.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("What this action should do: " + (dto.getIntent() == null ? "" : dto.getIntent()) +
                "\n\nAPI documentation:\n" + dto.getDocumentation());

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(history);
        llmRequest.add(user);

        String raw = aiSettingsService.sendLlmRequest(aiSettings, llmRequest);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> draft;
        try {
            draft = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[TemplateService] aiDiscoverEndpoints: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try shorter/cleaner documentation, or a different AI provider/model.");
        }
        sanitizeDiscoveredEndpoints(draft);

        aiChatService.appendMessage(sessionId, "user", user.getContent());
        aiChatService.appendMessage(sessionId, "assistant", cleaned);
        draft.put("sessionId", sessionId);
        return draft;
    }

    /** Whitelists aiDiscoverEndpoints' output: drops entries missing a usable method/title/docExcerpt, dedupes "id" (appending "-2", "-3"... on collision), caps docExcerpt length and total endpoint count so Step 2's prompt stays bounded — logs a warning whenever either cap is actually hit. */
    @SuppressWarnings("unchecked")
    private void sanitizeDiscoveredEndpoints(Map<String, Object> draft) {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        if (draft.get("endpoints") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> e = new LinkedHashMap<>((Map<String, Object>) o);
                if (!(e.get("method") instanceof String m) || !VALID_HTTP_METHODS.contains(m)) continue;
                if (!(e.get("title") instanceof String title) || title.isBlank()) continue;
                if (!(e.get("docExcerpt") instanceof String excerpt) || excerpt.isBlank()) continue;
                if (excerpt.length() > MAX_DOC_EXCERPT_CHARS) {
                    log.warn("[TemplateService] aiDiscoverEndpoints: docExcerpt for '{}' truncated from {} to {} chars",
                            title, excerpt.length(), MAX_DOC_EXCERPT_CHARS);
                    excerpt = excerpt.substring(0, MAX_DOC_EXCERPT_CHARS);
                    e.put("docExcerpt", excerpt);
                }
                String id = e.get("id") instanceof String s && !s.isBlank() ? s : title.toLowerCase().replaceAll("[^a-z0-9]+", "_");
                String uniqueId = id;
                int suffix = 2;
                while (!seenIds.add(uniqueId)) {
                    uniqueId = id + "-" + suffix++;
                }
                e.put("id", uniqueId);
                e.putIfAbsent("summary", "");
                e.put("recommended", Boolean.TRUE.equals(e.get("recommended")));
                endpoints.add(e);
                if (endpoints.size() >= MAX_DISCOVERED_ENDPOINTS) {
                    log.warn("[TemplateService] aiDiscoverEndpoints: capped at {} endpoints (model returned more)", MAX_DISCOVERED_ENDPOINTS);
                    break;
                }
            }
        }
        draft.put("endpoints", endpoints);
    }

    /**
     * Step 2 of the external_api wizard's guided AI flow — given ONE specific endpoint the admin
     * selected from Step 1's discovered list (its method + doc excerpt only, not the whole
     * document), drafts a call skeleton (method/urlTemplate/headers/auth/bodyTemplate, using raw
     * {{placeholder}} names — no ticket/workflow field mapping yet, that's Step 3/aiMapCallFields'
     * job) and enumerates every input the call needs. Continues the same "api_action_builder"
     * session Step 1 started.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> aiDraftCallSkeleton(AiDraftCallSkeletonRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getSelectedEndpoint() == null || dto.getSelectedEndpoint().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectedEndpoint is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            sessionId = aiChatService.createSession(userId, "api_action_builder", null).getId();
        }
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        ObjectMapper mapper = new ObjectMapper();
        String endpointJson;
        try {
            endpointJson = mapper.writeValueAsString(dto.getSelectedEndpoint());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid selectedEndpoint payload");
        }

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You are given ONE specific HTTP endpoint — its method and a doc excerpt describing \
                exactly this endpoint (not the whole API's documentation). Draft a call skeleton for \
                it and enumerate every input it needs. Produce a JSON object with EXACTLY this shape \
                (no markdown, no explanation, ONLY the JSON):
                {
                  "call": {
                    "name": "short_snake_case_name",
                    "method": "GET"|"POST"|"PUT"|"PATCH"|"DELETE",
                    "urlTemplate": "https://... using {{raw_param_name}} for every variable path/query part",
                    "headers": [{"key": "Header-Name", "valueTemplate": "value or {{raw_param_name}}"}],
                    "auth": {"type": "none"|"bearer"|"api_key"|"basic", "headerName": "only for api_key"},
                    "bodyTemplate": "JSON string body using {{raw_param_name}}, or empty string for no body"
                  },
                  "requiredInputs": [
                    {"placeholder": "must appear as {{...}} above", "description": "what this is, from the docs",
                     "required": true|false, "example": "example value from the docs, if shown"}
                  ]
                }
                Rules:
                - Use ONLY the given method + doc excerpt for THIS endpoint — do not reintroduce a
                  different endpoint discussed earlier in this conversation.
                - Placeholder names: only lowercase letters, numbers, underscores — no dots, dashes,
                  or spaces.
                - Every {{placeholder}} used in urlTemplate/headers/bodyTemplate MUST have exactly
                  one matching requiredInputs entry, and every requiredInputs entry MUST be
                  referenced by a {{placeholder}} somewhere — no orphans on either side.
                - NEVER include a real token/username/password value — only set auth.type (and
                  headerName for api_key) based on what the docs describe.
                - If auth.type isn't "none", do NOT also add a manual Authorization header.
                - If the docs don't specify an input confidently, still list it with "required":
                  false and note the uncertainty in "description" rather than silently omitting it —
                  never invent a param the docs never mention.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("What this action should do: " + (dto.getIntent() == null ? "" : dto.getIntent()) +
                "\n\nThe selected endpoint:\n" + endpointJson);

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(history);
        llmRequest.add(user);

        String raw = aiSettingsService.sendLlmRequest(aiSettings, llmRequest);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> result;
        try {
            result = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[TemplateService] aiDraftCallSkeleton: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a different AI provider/model.");
        }

        Map<String, Object> call = sanitizeCall(result.get("call") instanceof Map
                ? (Map<String, Object>) result.get("call") : new LinkedHashMap<>());
        call.put("id", UUID.randomUUID().toString());
        call.put("order", 0);
        call.put("responseCaptures", new ArrayList<>());

        List<Object> rawRequiredInputs = new ArrayList<>();
        if (result.get("requiredInputs") instanceof List<?> list) rawRequiredInputs.addAll(list);
        List<Object> requiredInputs = sanitizeRequiredInputs(call, rawRequiredInputs);

        aiChatService.appendMessage(sessionId, "user", user.getContent());
        aiChatService.appendMessage(sessionId, "assistant", cleaned);

        Map<String, Object> draftOut = new LinkedHashMap<>();
        draftOut.put("call", call);
        draftOut.put("requiredInputs", requiredInputs);
        draftOut.put("sessionId", sessionId);
        return draftOut;
    }

    /**
     * Cross-checks/repairs the correspondence between a Step-2 call skeleton's {{placeholder}}s and
     * its declared requiredInputs: normalizes any invalid placeholder character to "_" (in both the
     * call's templates and requiredInputs — there's no ticket./this. field-ref concept yet at this
     * stage, unlike repairCallPlaceholders, so this is a simpler, non-field-aware sibling of it),
     * drops a requiredInputs entry that's not actually referenced (the model can't be trusted to
     * only declare real ones), and — a real bug found live: a smaller/local model given a
     * many-parameter real endpoint (e.g. SerpAPI's Google Flights, 15+ query params) reliably gets
     * the URL right but drops half the requiredInputs entries, or omits the array's contents
     * almost entirely — SYNTHESIZES a generic entry for every placeholder actually used in the call
     * that the model didn't declare, rather than silently dropping it. Every {{placeholder}} in the
     * call is guaranteed a requiredInputs entry either way, so Step 3's mapping table always shows
     * every real input parameter regardless of how incomplete the model's own declaration was.
     */
    @SuppressWarnings("unchecked")
    private List<Object> sanitizeRequiredInputs(Map<String, Object> call, List<Object> rawRequiredInputs) {
        if (call.get("urlTemplate") instanceof String s) call.put("urlTemplate", normalizePlaceholderChars(s));
        if (call.get("bodyTemplate") instanceof String s) call.put("bodyTemplate", normalizePlaceholderChars(s));
        if (call.get("headers") instanceof List<?> headers) {
            for (Object h : headers) {
                if (h instanceof Map<?, ?> hmRaw && hmRaw.get("valueTemplate") instanceof String vt) {
                    ((Map<String, Object>) hmRaw).put("valueTemplate", normalizePlaceholderChars(vt));
                }
            }
        }

        Set<String> usedPlaceholders = new LinkedHashSet<>();
        for (String key : List.of("urlTemplate", "bodyTemplate")) {
            if (call.get(key) instanceof String s) usedPlaceholders.addAll(placeholdersIn(s));
        }
        if (call.get("headers") instanceof List<?> headers) {
            for (Object h : headers) {
                if (h instanceof Map<?, ?> hm && hm.get("valueTemplate") instanceof String vt) {
                    usedPlaceholders.addAll(placeholdersIn(vt));
                }
            }
        }

        Map<String, Object> byPlaceholder = new LinkedHashMap<>();
        for (Object o : rawRequiredInputs) {
            if (!(o instanceof Map<?, ?> m)) continue;
            if (!(m.get("placeholder") instanceof String rawP) || rawP.isBlank()) continue;
            String p = rawP.matches("[A-Za-z0-9_]+") ? rawP : rawP.replaceAll("[^A-Za-z0-9_]", "_");
            if (!usedPlaceholders.contains(p)) {
                log.warn("[TemplateService] aiDraftCallSkeleton: requiredInputs placeholder '{}' isn't referenced in the call, dropping", p);
                continue;
            }
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("placeholder", p);
            clean.put("description", m.get("description") instanceof String d ? d : "");
            clean.put("required", Boolean.TRUE.equals(m.get("required")));
            if (m.get("example") instanceof String ex) clean.put("example", ex);
            byPlaceholder.put(p, clean);
        }
        for (String p : usedPlaceholders) {
            if (byPlaceholder.containsKey(p)) continue;
            log.warn("[TemplateService] aiDraftCallSkeleton: placeholder '{}' used in the call had no requiredInputs entry, synthesizing a generic one", p);
            Map<String, Object> synthesized = new LinkedHashMap<>();
            synthesized.put("placeholder", p);
            synthesized.put("description", "");
            synthesized.put("required", true);
            byPlaceholder.put(p, synthesized);
        }
        return new ArrayList<>(byPlaceholder.values());
    }

    private Set<String> placeholdersIn(String template) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = ANY_PLACEHOLDER.matcher(template);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    private String normalizePlaceholderChars(String template) {
        Matcher m = ANY_PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String raw = m.group(1);
            String safe = raw.matches("[A-Za-z0-9_]+") ? raw : raw.replaceAll("[^A-Za-z0-9_]", "_");
            result.append(template, last, m.start()).append("{{").append(safe).append("}}");
            last = m.end();
        }
        result.append(template.substring(last));
        return result.toString();
    }

    /**
     * Step 3 of the external_api wizard's guided AI flow — given Step 2's requiredInputs, maps each
     * one to a real ticket/workflow field (inventing a new workflow field + missingWorkflowFields
     * entry when nothing fits). Continues the same "api_action_builder" session Steps 1-2 already
     * used. Reuses sanitizeRequestMappings/sanitizeMissingWorkflowFields unchanged — this method's
     * output shape already fits them exactly (they're the same helpers aiFixCall already uses).
     */
    public Map<String, Object> aiMapCallFields(AiMapCallFieldsRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getRequiredInputs() == null || dto.getRequiredInputs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requiredInputs is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            sessionId = aiChatService.createSession(userId, "api_action_builder", null).getId();
        }
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        ObjectMapper mapper = new ObjectMapper();
        String requiredInputsJson;
        try {
            requiredInputsJson = mapper.writeValueAsString(dto.getRequiredInputs());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requiredInputs payload");
        }

        String ticketFieldList = workflowFieldsJson(dto.getTicketFields());
        String workflowFieldList = workflowFieldsJson(dto.getWorkflowFields());

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                Given one HTTP call's required inputs (each a placeholder + description of what it \
                needs) and the full ticket/workflow field catalogs, map each required input to the \
                best-fitting existing field, or invent a new workflow field when nothing fits. \
                Produce a JSON object with EXACTLY this shape (no markdown, no explanation, ONLY the \
                JSON):
                {
                  "requestMappings": [{"placeholder": "must exactly match one requiredInputs placeholder",
                                        "ticketField": "ticket.<field> or this.<workflow field>"}],
                  "missingWorkflowFields": [{"suggestedFieldKey": "snake_case_key", "suggestedLabel": "Human Label",
                                              "suggestedFieldType": "text"|"number"|"date"|"checkbox"}]
                }
                Rules:
                - Every requiredInputs entry with "required": true MUST get exactly one
                  requestMappings entry. An entry with "required": false MAY be left unmapped if
                  nothing genuinely fits.
                - "ticketField" MUST start with either "ticket." (from the available ticket fields)
                  or "this." (from the available workflow fields) — never a bare field name.
                - Never map a required input to a field whose "type" is "nodelist" — that type holds
                  a growing LIST of text entries, not the single scalar value a call placeholder
                  needs. Treat it as unusable as a source here, exactly like a type that plainly
                  doesn't fit.
                - MANDATORY CHECK for every single "this.<key>" you write, with no exceptions: does
                  <key> EXACTLY match a key already in the provided workflow fields list, AND is that
                  field's "type" a genuinely good fit? If YES, use that exact key. If NO, you MUST do
                  BOTH: (1) still write "this.<key>" (invent a new snake_case key if needed), AND (2)
                  add exactly one matching entry to "missingWorkflowFields". Every "this.<key>" you
                  reference must be either an existing workflow field or declared in
                  "missingWorkflowFields" — never neither. "suggestedFieldType" MUST be exactly one
                  of "text", "number", "date", or "checkbox".
                - Keep it minimal — only genuinely-needed mappings, not every field that happens to
                  exist.
                - If earlier turns are present in this conversation (a re-run after refined
                  instructions or a manual edit), keep prior mappings still valid and only change
                  what the newest instruction/context implies should change.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Available ticket fields: " + ticketFieldList +
                "\n\nAvailable workflow fields: " + workflowFieldList +
                "\n\nWhat this action should do: " + (dto.getIntent() == null ? "" : dto.getIntent()) +
                "\n\nAPI documentation:\n" + (dto.getDocumentation() == null ? "" : dto.getDocumentation()) +
                "\n\nRequired inputs for this call:\n" + requiredInputsJson);

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
            log.warn("[TemplateService] aiMapCallFields: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a different AI provider/model.");
        }

        Set<String> existingKeys = existingWorkflowFieldKeys(dto.getWorkflowFields());
        List<Map<String, Object>> missingWorkflowFields = sanitizeMissingWorkflowFields(draft, existingKeys);

        // A real bug found live: "Map Fields with AI" always came back with an EMPTY mapping table
        // whenever the model (correctly, per this method's own system prompt) invented a brand-new
        // "this.<key>" for a field that doesn't exist yet — sanitizeRequestMappings only ever
        // whitelisted a "this.<key>" mapping against the PRE-EXISTING workflow field catalog, so
        // every mapping paired with a missingWorkflowFields suggestion was silently dropped. Since
        // this is the common case for a brand-new action item (no custom workflow fields exist
        // yet), the mapping table came back empty essentially every time, looking exactly like the
        // feature "not working" rather than a partial, still-useful result. The two lists are meant
        // to travel together — WorkflowFieldSuggestions/missingFieldUsedBy on the frontend already
        // expects a mapping row to exist pointing at a not-yet-created suggested field, so the
        // suggestion card can show what it's used by. Fix: also allow any key this same AI response
        // just proposed in missingWorkflowFields, not only ones that already exist.
        Set<String> allowedWorkflowKeys = new HashSet<>(existingKeys);
        for (Map<String, Object> suggestion : missingWorkflowFields) {
            if (suggestion.get("suggestedFieldKey") instanceof String key) allowedWorkflowKeys.add(key);
        }

        List<Object> rawMappings = new ArrayList<>();
        if (draft.get("requestMappings") instanceof List<?> list) rawMappings.addAll(list);
        List<Object> requestMappings = sanitizeRequestMappings(rawMappings, dto.getTicketFields(), allowedWorkflowKeys);

        aiChatService.appendMessage(sessionId, "user", user.getContent());
        aiChatService.appendMessage(sessionId, "assistant", cleaned);

        Map<String, Object> draftOut = new LinkedHashMap<>();
        draftOut.put("requestMappings", requestMappings);
        draftOut.put("missingWorkflowFields", missingWorkflowFields);
        draftOut.put("sessionId", sessionId);
        return draftOut;
    }

    /**
     * A real bug found live: draft-from-scratch responseCaptures JSONPaths are pure guesses against
     * prose documentation (external_api) or a schema-inferred guess (mcp_tool's resultPath) — an
     * admin's real API ("Flight search", SerpApi-shaped) came back with a completely different
     * structure than guessed, so nothing ever captured or mapped into the ticket. This method
     * re-derives responseCaptures/resultPath + fieldMappings.response grounded in a REAL response
     * captured via "Test this call now" (TestActionModal.tsx's "Auto-map from this response"
     * action) — it only ever refines the response side of ALREADY-EXISTING calls; it never
     * invents/reorders call ids and never touches url/method/headers/auth/bodyTemplate/toolName/
     * argumentMappings. For external_api (Step 5 of the guided wizard flow), this continues the same
     * "api_action_builder" session Steps 1-3 already used, via the same sessionId thread aiFixCall/
     * aiMapCallFields already established — so the AI already knows the original documentation/
     * intent/field-mapping context. For mcp_tool the frontend never sends a sessionId (that branch
     * is explicitly unchanged), so this simply creates a fresh single-turn session each call, same
     * one-shot behavior as before.
     *
     * A real bug found live via admin debugging: this used to be ONE combined LLM call asking the
     * model to extract captures, write summaries, AND choose target fields (with a whole
     * ticket-vs-workflow "MANDATORY CHECK" decision tree) all at once — 20+ effectively-simultaneous
     * instructions once bundled sub-rules are counted, too many for a small local model to reliably
     * follow together. Split into two smaller, focused, SESSION-CONTINUING calls instead — mirrors
     * the same split already proven on the request side of this wizard (aiDraftCallSkeleton vs.
     * aiMapCallFields): {@link #aiExtractResponseCaptures} finds/summarizes captures only, then
     * {@link #aiMapResponseCaptures} maps just those captures to targets only, reusing
     * aiMapCallFields' already-proven prompt shape almost verbatim. Two round-trips instead of one —
     * an accepted latency cost for reliability, not a defect. The public shape returned here is
     * unchanged either way, so no caller (frontend or otherwise) needed to change.
     */
    public Map<String, Object> aiRefineResponseMapping(AiRefineResponseMappingRequestDto dto, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getCalls() == null || dto.getCalls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "calls is required — run a live test first");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            sessionId = aiChatService.createSession(userId, "api_action_builder", null).getId();
        }
        List<LlmStructure> history = aiChatService.getMessageHistory(sessionId, userId);

        ObjectMapper mapper = new ObjectMapper();

        // A real improvement made live: each call's rawResponse can be up to 200KB (the cap the
        // executors use for their trace/UI-preview field — reasonable for a human to scroll, or
        // for a large cloud model's context window), but a small local model (e.g. Ollama's
        // gemma4:e2b) can be overwhelmed by a real API response of even ~9-29KB. Blind character
        // truncation was tried first and made things WORSE, not better (a smaller 8KB cut produced
        // an even less coherent reply than the full 29KB one) — the model wasn't just struggling
        // with raw size, it was struggling with a huge, mostly-REPETITIVE document (many near-
        // identical array entries, e.g. SerpApi's 3-entry "best_flights" / 6-entry "other_flights").
        // A model only needs ONE representative example of a repeated structure to derive a correct
        // index-based JSONPath (e.g. "$.best_flights[0].flights[0].airline") — not all of them — so
        // this compacts every JSON array down to just its first element, recursively at every
        // nesting level, before the response ever reaches the prompt. Falls back to a plain char
        // cap for a non-JSON (plain text) response, where there's no array structure to compact.
        for (AiRefineCallInputDto c : dto.getCalls()) {
            c.setRawResponse(compactRawResponseForPrompt(c.getRawResponse(), mapper));
        }

        String callsJson;
        try {
            callsJson = mapper.writeValueAsString(dto.getCalls());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid calls payload");
        }

        boolean isMcp = "mcp_tool".equals(dto.getType());
        Set<String> existingCallIds = new HashSet<>();
        for (AiRefineCallInputDto c : dto.getCalls()) {
            if (c.getId() != null) existingCallIds.add(c.getId());
        }

        ExtractionResult extraction = aiExtractResponseCaptures(
                aiSettings, history, mapper, dto.getIntent(), dto.getSpecificAsk(), dto.getDocumentation(),
                callsJson, isMcp, existingCallIds);
        int totalCaptures = extraction.calls().stream()
                .mapToInt(c -> ((List<?>) c.get("responseCaptures")).size()).sum();
        // Mirrors evaluateResponseCaptures' precedent elsewhere in this codebase: a totally-empty
        // result must be a visible error, not a silent no-op the admin has to notice is wrong.
        if (totalCaptures == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI could not find any capturable values in the response(s) — try a shorter/clearer intent, or a different AI provider/model.");
        }
        aiChatService.appendMessage(sessionId, "user", extraction.userContent());
        aiChatService.appendMessage(sessionId, "assistant", extraction.cleanedJson());

        // Continuing the SAME session for Call 2 — built by locally appending Call 1's own turns to
        // the in-memory list already loaded above, rather than re-querying aiChatService.
        // getMessageHistory: that method just filters persisted rows to non-system roles in
        // insertion order, so a second DB round-trip (plus its redundant session-ownership check)
        // would produce an object-equivalent list for no benefit — nothing about the session's
        // validity could have changed between these two calls within the same request.
        List<LlmStructure> historyForMapping = new ArrayList<>(history);
        LlmStructure extractionUserTurn = new LlmStructure();
        extractionUserTurn.setRole("user");
        extractionUserTurn.setContent(extraction.userContent());
        LlmStructure extractionAssistantTurn = new LlmStructure();
        extractionAssistantTurn.setRole("assistant");
        extractionAssistantTurn.setContent(extraction.cleanedJson());
        historyForMapping.add(extractionUserTurn);
        historyForMapping.add(extractionAssistantTurn);

        String fieldList = workflowFieldsJson(dto.getTicketFields());
        String workflowFieldList = workflowFieldsJson(dto.getWorkflowFields());

        List<Map<String, Object>> responseMappings = new ArrayList<>();
        List<Map<String, Object>> missingWorkflowFields = new ArrayList<>();
        try {
            MappingResult mapping = aiMapResponseCaptures(
                    aiSettings, historyForMapping, mapper, dto.getIntent(), dto.getSpecificAsk(),
                    fieldList, workflowFieldList, extraction.calls(),
                    existingWorkflowFieldKeys(dto.getWorkflowFields()));
            responseMappings = mapping.responseMappings();
            missingWorkflowFields = mapping.missingWorkflowFields();
            aiChatService.appendMessage(sessionId, "user", mapping.userContent());
            aiChatService.appendMessage(sessionId, "assistant", mapping.cleanedJson());
        } catch (ResponseStatusException e) {
            // Partial success (mirrors ExternalApiActionExecutor.evaluateResponseCaptures' graceful-
            // degradation design): the captures themselves are still genuinely useful even if
            // targeting failed — the admin can still see/verify them and hand-map targets via the
            // existing manual table, rather than losing Call 1's entire result to a Call 2 hiccup.
            log.warn("[TemplateService] aiRefineResponseMapping: mapping step failed after a successful extraction ({} captures) — returning captures with empty fieldMappings. {}",
                    totalCaptures, e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("calls", extraction.calls());
        result.put("fieldMappings", Map.of("response", responseMappings));
        result.put("missingWorkflowFields", missingWorkflowFields);
        result.put("sessionId", sessionId);
        return result;
    }

    private record ExtractionResult(List<Map<String, Object>> calls, String userContent, String cleanedJson) {}

    /**
     * Call 1 of the aiRefineResponseMapping split — extraction ONLY, nothing about targets.
     * Ticket/workflow field catalogs aren't even sent here; they have nothing to do with deciding
     * WHICH values are worth capturing or WHERE they live in the response.
     */
    @SuppressWarnings("unchecked")
    private ExtractionResult aiExtractResponseCaptures(
            AiSettingsEntity aiSettings, List<LlmStructure> history, ObjectMapper mapper,
            String intent, String specificAsk, String documentation, String callsJson,
            boolean isMcp, Set<String> existingCallIds)
            throws IOException, URISyntaxException, InterruptedException {
        String pathKey = isMcp ? "resultPath" : "jsonPath";

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        // "__PATH_KEY__" is a placeholder replaced below with "jsonPath" (external_api) or
        // "resultPath" (mcp_tool) — kept as a token substitution rather than splicing the text
        // block itself, since Java text blocks can't be interrupted mid-literal by a variable.
        String systemTemplate = """
                You are given the REAL response of one or more calls that were just executed LIVE, \
                each already captured via a "Test this call now" run — this is ground truth, not \
                documentation, not a guess. For each call whose "rawResponse" is non-empty, you'll \
                see a JSON array of every real {"path": ..., "value": ...} pair available in that \
                response (already flattened for you — you never need to construct or guess a path \
                yourself, only COPY one verbatim from this list). Some entries additionally have \
                "isRepeatableList": true — these represent a whole JSON array (see the whole-array \
                rule below for when to copy one of THESE paths instead of a plain leaf path). If a \
                call's rawResponse couldn't be flattened (plain text, not JSON), you'll see the raw \
                text instead — in that case "$.text" is the only usable __PATH_KEY__ for it. Your \
                ONLY job here is extraction — deciding WHICH values are worth capturing and WHERE \
                they live in the response. You are NOT deciding where each captured value should be \
                stored on the ticket/workflow — that happens in a separate step, so do not return \
                any field-mapping or target information here. Produce a JSON object with EXACTLY \
                this shape (no markdown, no explanation, ONLY the JSON):
                {
                  "calls": [
                    {
                      "id": "must be copied byte-for-byte from the matching input call's id — never invent, omit, or reorder",
                      "responseCaptures": [{"name": "camelCaseName", "__PATH_KEY__": "copied EXACTLY, character-for-character, from one 'path' entry given for this call", "summary": "one short (max 2 sentences) human-readable description of what this captured value actually IS"}]
                    }
                  ]
                }
                Rules:
                - Only include an entry in "calls" for an input call that had real path/value data (or
                  raw text) available, and only include a responseCapture you're genuinely confident
                  is a real, useful value — if a call has nothing worth capturing, omit it entirely
                  rather than guessing.
                - Every "__PATH_KEY__" value MUST be copied EXACTLY from one of the given "path" \
                  entries for that call — do not shorten it, add an index, remove one, or otherwise \
                  modify it. Copying the wrong entry is a real, common mistake — when two entries \
                  look similar, read BOTH entries' full path text and pick the one whose path name \
                  most precisely and completely matches what you're actually capturing. For example, \
                  if capturing an overall/total value, a path containing "total_..." is almost always \
                  correct over a similarly-named path nested one level deeper that's actually a \
                  single per-item value (e.g. a whole trip's "total_duration" vs. one leg's plain \
                  "duration" — these are DIFFERENT values at DIFFERENT paths, not interchangeable). \
                  If capturing a price/cost, prefer a path literally containing "price" or "cost" \
                  closest to the top of its containing object over a deeper or unrelated one.
                - Do NOT return "url", "method", "headers", "auth", "bodyTemplate", "toolName", or \
                  "argumentMappings" for any call, and do NOT return "fieldMappings" or \
                  "missingWorkflowFields" at all — you are only ever extracting response captures \
                  here, never how the request is made and never where a value is targeted. Any such \
                  key you include will be discarded anyway.
                - responseCaptures[].name (the name paired with each "__PATH_KEY__") MUST contain \
                  ONLY lowercase letters, numbers, and underscores — no dots, dashes, or spaces.
                - When the real API response contains a JSON array where EACH ELEMENT is itself a \
                  meaningful separate item (e.g. one entry per flight option, one per search result) \
                  and the admin's intent implies the whole set matters (not just one), capture the \
                  WHOLE array by copying the path from the entry marked "isRepeatableList": true \
                  EXACTLY as given, with no "[N]" index appended — that captures every real element \
                  at run time, not just the single compacted preview element you see in this prompt. \
                  Otherwise, capture one specific indexed leaf path as usual.
                - "summary" must describe what the captured value actually contains, using the real \
                  "value" (or, for an "isRepeatableList": true path, the compacted one-element \
                  preview plus the fact that every real element will be captured at run time) \
                  already given to you for that exact path — never guess or just restate the path \
                  name. Keep it to one, at most two, short plain sentences — no markdown, no HTML. \
                  This exists so an admin can visually verify the capture is correct without leaving \
                  this screen, so be concrete (e.g. "The total price in USD of the cheapest flight \
                  option" rather than "A price value").
                - If earlier turns are present in this conversation, you already know the original \
                  documentation/intent context from them — use it, and treat the admin's original \
                  intent as the guide for which captures are genuinely useful, not just what the \
                  response happens to contain.
                """;
        system.setContent(systemTemplate.replace("__PATH_KEY__", pathKey));

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        String specificAskSection = (specificAsk == null || specificAsk.isBlank())
                ? "" : "\n\nFor THIS run specifically, the admin additionally wants: " + specificAsk +
                        " — treat this as a more specific, higher-priority refinement of the broad " +
                        "intent above, not a replacement for it: still only capture genuinely " +
                        "present, correct data, but prioritize capturing what satisfies this " +
                        "specific ask over other equally-valid captures you might otherwise propose.";
        String docsSection = (documentation == null || documentation.isBlank())
                ? "" : "\n\nAPI documentation (use this to disambiguate a generic/abbreviated real " +
                        "response key when its intended meaning isn't obvious from the key name alone):\n" + documentation;
        user.setContent("What this action should do: " + (intent == null ? "" : intent) +
                specificAskSection +
                docsSection +
                "\n\nCalls, each with its real response already flattened into every available " +
                "{\"path\":...,\"value\":...} pair you may copy a path from (JSON):\n" + callsJson);

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
            log.warn("[TemplateService] aiExtractResponseCaptures: model response was not valid JSON ({}); raw response(s) totaled {} chars. Model output: {}",
                    e.getMessage(), callsJson.length(), cleaned.length() > 4000 ? cleaned.substring(0, 4000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a shorter/clearer intent, or a different AI provider/model.");
        }

        List<Map<String, Object>> calls = sanitizeExtractedCalls(draft, existingCallIds, isMcp);
        return new ExtractionResult(calls, user.getContent(), cleaned);
    }

    private record MappingResult(List<Map<String, Object>> responseMappings,
                                  List<Map<String, Object>> missingWorkflowFields,
                                  String userContent, String cleanedJson) {}

    /**
     * Call 2 of the aiRefineResponseMapping split — targeting ONLY, given just the capture
     * names+summaries Call 1 already produced (never the raw response or flattened path list again
     * — nothing new to extract here). Near-verbatim mirror of aiMapCallFields' already-proven prompt
     * shape, adapted for the response side.
     */
    @SuppressWarnings("unchecked")
    private MappingResult aiMapResponseCaptures(
            AiSettingsEntity aiSettings, List<LlmStructure> historyIncludingExtractionTurn, ObjectMapper mapper,
            String intent, String specificAsk, String fieldList, String workflowFieldList,
            List<Map<String, Object>> extractedCalls, Set<String> existingWorkflowFieldKeys)
            throws IOException, URISyntaxException, InterruptedException {
        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                Given a list of values that were just extracted from a real API response (each a \
                capture name + a short summary of what it actually is) and the full ticket/workflow \
                field catalogs, map each capture to the best-fitting existing target field, or \
                invent a new workflow field when nothing fits. Produce a JSON object with EXACTLY \
                this shape (no markdown, no explanation, ONLY the JSON):
                {
                  "fieldMappings": {
                    "response": [{"captureName": "must exactly match one of the given capture names", "target": "ticket.<field> or this.<workflow field>"}]
                  },
                  "missingWorkflowFields": [{"suggestedFieldKey": "snake_case_key", "suggestedLabel": "Human Label",
                                              "suggestedFieldType": "text"|"number"|"date"|"checkbox"}]
                }
                Rules:
                - Only map a capture whose meaning (from its summary) is genuinely useful to store —
                  a capture you're not confident belongs anywhere is better left unmapped than forced
                  onto a mismatched target.
                - Read the capture's actual meaning (from its summary) and choose a target whose own
                  name means the SAME thing — e.g. a captured price/cost must never map to a field
                  about a location/airport/destination, and vice versa; if you cannot honestly say
                  the target field's name and the capture's meaning match, it is WRONG, even if that
                  ticket field happens to be in the available list. Ticket fields (from the available
                  list) can ONLY be selected if a genuinely matching one exists as-is — you cannot
                  create a new one.
                - "target" MUST start with either "ticket." (from the available ticket fields) or
                  "this." (from the available workflow fields) — never a bare field name.
                - MANDATORY CHECK for every single "this.<key>" you write, with no exceptions: does
                  <key> EXACTLY match a key already in the provided workflow fields list, AND is that
                  field's "type" a genuinely good fit for this capture? If YES, use that exact key.
                  If NO, you MUST do BOTH: (1) still write "this.<key>" (invent a new snake_case key
                  if needed), AND (2) add exactly one matching entry to "missingWorkflowFields".
                  Every "this.<key>" you reference must be either an existing workflow field or
                  declared in "missingWorkflowFields" — never neither. Given the choice between
                  forcing a mismatched existing ticket field and inventing a well-named new workflow
                  field, ALWAYS invent the new workflow field instead — a made-up field with the
                  right name and type is far more useful than a real field with the wrong meaning.
                  "suggestedFieldType" MUST be exactly one of "text", "number", "date", or
                  "checkbox".
                - A target field whose "type" (in the available ticket/workflow field lists) is
                  "nodelist" stores a growing list of short readable text entries, not a single value
                  — mapping a capture to it ADDS a new entry rather than overwriting anything, and
                  the executor automatically turns a captured JSON object/array into readable
                  "key: value, key2: value2" text for you, so you never need to pre-format it
                  yourself. It's a good target for a capture whose summary describes a whole
                  collection of items (e.g. one entry per flight option) rather than a single scalar.
                - Keep it minimal — only genuinely-needed mappings, not every capture that happens to
                  exist.
                - If earlier turns are present in this conversation (including the extraction step
                  that just ran, and any documentation/intent given there, or a re-run after refined
                  instructions/a manual edit), you already know that context — use it, and keep prior
                  mappings still valid, only changing what the newest instruction/context implies
                  should change.
                """);

        List<Map<String, Object>> captureRefs = new ArrayList<>();
        for (Map<String, Object> call : extractedCalls) {
            if (!(call.get("responseCaptures") instanceof List<?> caps)) continue;
            for (Object capObj : caps) {
                if (!(capObj instanceof Map<?, ?> cap)) continue;
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("captureName", cap.get("name"));
                ref.put("summary", cap.get("summary"));
                captureRefs.add(ref);
            }
        }
        String capturesJson = mapper.writeValueAsString(captureRefs);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        String specificAskSection = (specificAsk == null || specificAsk.isBlank())
                ? "" : "\n\nFor THIS run specifically, the admin additionally wants: " + specificAsk +
                        " — when multiple captures could reasonably map to a limited set of target " +
                        "fields, prioritize satisfying this specific ask over other equally-valid " +
                        "mappings you might otherwise propose.";
        user.setContent("Available ticket fields: " + fieldList +
                "\n\nAvailable workflow fields: " + workflowFieldList +
                "\n\nWhat this action should do: " + (intent == null ? "" : intent) +
                specificAskSection +
                "\n\nCaptured values from the real response, each with what it actually is:\n" + capturesJson);

        List<LlmStructure> llmRequest = new ArrayList<>();
        llmRequest.add(system);
        llmRequest.addAll(historyIncludingExtractionTurn);
        llmRequest.add(user);

        String raw = aiSettingsService.sendLlmRequest(aiSettings, llmRequest);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> draft;
        try {
            draft = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[TemplateService] aiMapResponseCaptures: model response was not valid JSON ({}): {}",
                    e.getMessage(), cleaned.length() > 2000 ? cleaned.substring(0, 2000) + "…" : cleaned);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — try a different AI provider/model.");
        }

        Set<String> validCaptureNames = new HashSet<>();
        for (Map<String, Object> ref : captureRefs) {
            if (ref.get("captureName") instanceof String s) validCaptureNames.add(s);
        }
        List<Map<String, Object>> responseMappings = sanitizeResponseFieldMappings(draft, validCaptureNames);
        List<Map<String, Object>> missingWorkflowFields = sanitizeMissingWorkflowFields(draft, existingWorkflowFieldKeys);
        return new MappingResult(responseMappings, missingWorkflowFields, user.getContent(), cleaned);
    }

    private static final int MAX_RAW_RESPONSE_FOR_PROMPT_CHARS = 10_000;
    private static final int MAX_FLATTENED_LEAF_ENTRIES = 150;
    private static final int MAX_CAPTURE_SUMMARY_CHARS = 300; // roughly two short sentences

    /**
     * See the comment at aiRefineResponseMapping's call site for the array-compaction reasoning.
     * A second, later improvement made live after array-compaction alone still produced a WRONG
     * (if syntactically valid) mapping from a free local model: it picked "$.price" (doesn't
     * exist) over the real "$.best_flights[0].price", and a per-segment "duration" (135) over the
     * real trip "total_duration" (885) — both cases of the model losing track of exactly where a
     * value lived while mentally reconstructing a path from nested JSON. Rather than asking the
     * model to CONSTRUCT a path (error-prone), this flattens the compacted tree into a list of
     * every real {path, value} pair up front — the model only has to COPY the correct entry's
     * path verbatim, and seeing e.g. "$.best_flights[0].flights[0].duration" and
     * "$.best_flights[0].total_duration" as two separate, clearly-labeled list entries (instead
     * of buried at different depths in a nested tree) makes the semantic difference far more
     * visually obvious than nested JSON does.
     */
    private String compactRawResponseForPrompt(String rawResponse, ObjectMapper mapper) {
        if (rawResponse == null || rawResponse.isBlank()) return rawResponse;
        try {
            JsonNode tree = mapper.readTree(rawResponse);
            JsonNode compactedTree = compactArraysToFirstElement(tree, mapper);
            List<Map<String, Object>> flattened = new ArrayList<>();
            flattenToPathValueList(compactedTree, "$", flattened);
            String result = mapper.writeValueAsString(flattened);
            return result.length() > MAX_RAW_RESPONSE_FOR_PROMPT_CHARS
                    ? result.substring(0, MAX_RAW_RESPONSE_FOR_PROMPT_CHARS) + "…" : result;
        } catch (Exception e) {
            // Not valid JSON (a plain-text response, or malformed) — nothing to compact/flatten,
            // fall back to a plain character cap.
            return rawResponse.length() > MAX_RAW_RESPONSE_FOR_PROMPT_CHARS
                    ? rawResponse.substring(0, MAX_RAW_RESPONSE_FOR_PROMPT_CHARS) + "…(truncated for the AI prompt)" : rawResponse;
        }
    }

    /** Recursively keeps only the first element of every JSON array (still compacted itself) — a repeated structure only needs one representative example to derive an index-based JSONPath from. Objects and scalars pass through unchanged (recursing into object field values). */
    private JsonNode compactArraysToFirstElement(JsonNode node, ObjectMapper mapper) {
        if (node.isArray()) {
            ArrayNode compacted = mapper.createArrayNode();
            if (node.size() > 0) {
                compacted.add(compactArraysToFirstElement(node.get(0), mapper));
            }
            return compacted;
        }
        if (node.isObject()) {
            ObjectNode compacted = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                compacted.set(entry.getKey(), compactArraysToFirstElement(entry.getValue(), mapper));
            }
            return compacted;
        }
        return node; // scalar (string/number/boolean/null) — unchanged
    }

    /**
     * Walks an (already array-compacted) tree and collects one {path, value} entry per leaf/scalar
     * — the exact, ready-to-copy jsonPath strings the prompt hands the model instead of nested
     * JSON. Capped at MAX_FLATTENED_LEAF_ENTRIES to bound prompt size on a response with very many
     * distinct top-level fields.
     *
     * Also emits one extra entry for the array itself at every array node (path with no trailing
     * index, "isRepeatableList": true, value = the already-compacted one-element preview) — added
     * to support mapping a whole array to a "nodelist" target field (see aiRefineResponseMapping's
     * system prompt): the executor turns each real element of that array into one human-readable
     * node at run time, not just the single compacted preview element seen here.
     */
    private void flattenToPathValueList(JsonNode node, String path, List<Map<String, Object>> out) {
        if (out.size() >= MAX_FLATTENED_LEAF_ENTRIES) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext() && out.size() < MAX_FLATTENED_LEAF_ENTRIES) {
                Map.Entry<String, JsonNode> entry = fields.next();
                flattenToPathValueList(entry.getValue(), path + "." + entry.getKey(), out);
            }
        } else if (node.isArray()) {
            Map<String, Object> arrayEntry = new LinkedHashMap<>();
            arrayEntry.put("path", path);
            arrayEntry.put("value", node);
            arrayEntry.put("isRepeatableList", true);
            out.add(arrayEntry);
            for (int i = 0; i < node.size() && out.size() < MAX_FLATTENED_LEAF_ENTRIES; i++) {
                flattenToPathValueList(node.get(i), path + "[" + i + "]", out);
            }
        } else {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", path);
            entry.put("value", leafValue(node));
            out.add(entry);
        }
    }

    private Object leafValue(JsonNode node) {
        if (node.isTextual()) return node.textValue();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNull()) return null;
        return node.toString();
    }

    /**
     * Validates Call 1's (aiExtractResponseCaptures) raw output — call ids must be preserved exactly
     * (never reassigned) and every other call field (url/auth/arguments/etc.) must never appear at
     * all so a hallucinated echo of them can never accidentally get merged back over the real
     * request-side config by the frontend's id-matched merge. "summary" is a best-effort review aid
     * that never gates the capture (trim, cap at MAX_CAPTURE_SUMMARY_CHARS, default "").
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sanitizeExtractedCalls(Map<String, Object> draft, Set<String> existingCallIds, boolean isMcp) {
        String pathKey = isMcp ? "resultPath" : "jsonPath";
        List<Map<String, Object>> calls = new ArrayList<>();
        if (draft.get("calls") instanceof List<?> list) {
            for (Object c : list) {
                if (!(c instanceof Map)) continue;
                Map<String, Object> call = (Map<String, Object>) c;
                String id = call.get("id") instanceof String s ? s : null;
                if (id == null || !existingCallIds.contains(id)) continue; // drop hallucinated/unknown ids

                List<Map<String, Object>> captures = new ArrayList<>();
                if (call.get("responseCaptures") instanceof List<?> capList) {
                    for (Object capObj : capList) {
                        if (!(capObj instanceof Map)) continue;
                        Map<String, Object> cap = (Map<String, Object>) capObj;
                        Object name = cap.get("name");
                        Object path = cap.get(pathKey);
                        if (!(name instanceof String) || ((String) name).isBlank()) continue;
                        if (!(path instanceof String) || ((String) path).isBlank()) continue;
                        String summary = cap.get("summary") instanceof String s ? s.trim() : "";
                        if (summary.length() > MAX_CAPTURE_SUMMARY_CHARS) {
                            summary = summary.substring(0, MAX_CAPTURE_SUMMARY_CHARS) + "…";
                        }
                        Map<String, Object> cleanCap = new LinkedHashMap<>();
                        cleanCap.put("name", name);
                        cleanCap.put(pathKey, path);
                        cleanCap.put("summary", summary);
                        captures.add(cleanCap);
                    }
                }

                Map<String, Object> cleanCall = new LinkedHashMap<>();
                cleanCall.put("id", id);
                cleanCall.put("responseCaptures", captures);
                calls.add(cleanCall);
            }
        }
        return calls;
    }

    /**
     * Validates Call 2's (aiMapResponseCaptures) raw output. "captureName" must match one Call 1
     * actually produced — not just be non-blank — a cheap, real hallucination-guard the two-call
     * split newly enables (Call 1's real names are known ground truth before Call 2 even runs, which
     * wasn't true back when this was one combined call).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sanitizeResponseFieldMappings(Map<String, Object> draft, Set<String> validCaptureNames) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> fm = draft.get("fieldMappings") instanceof Map
                ? (Map<String, Object>) draft.get("fieldMappings") : Map.of();
        if (fm.get("response") instanceof List<?> respList) {
            for (Object respObj : respList) {
                if (!(respObj instanceof Map)) continue;
                Map<String, Object> resp = (Map<String, Object>) respObj;
                Object captureName = resp.get("captureName");
                Object target = resp.get("target");
                if (!(captureName instanceof String cn) || cn.isBlank()) continue;
                if (!validCaptureNames.contains(cn)) continue;
                if (!(target instanceof String) || ((String) target).isBlank()) continue;
                Map<String, Object> clean = new LinkedHashMap<>();
                clean.put("captureName", captureName);
                clean.put("target", target);
                out.add(clean);
            }
        }
        return out;
    }

    private Map<String, Object> buildDefaultLayout() {
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, "title",        "text",      true, 1, "",    "full",  null);
        addField(fields, "description",  "rich-text", true, 2, "",    "full",  null);
        addField(fields, "status",       "combobox",  true, 3, "new", "half",
                List.of("new", "open", "in_progress", "waiting", "resolved", "closed"));
        addField(fields, "priority",     "combobox",  true, 4, "medium", "half",
                List.of("critical", "high", "medium", "low"));
        addField(fields, "request_user", "text",      true, 5, "",    "half",  null);
        addField(fields, "responsible",  "text",      true, 6, "",    "half",  null);
        addField(fields, "attachments",  "attachments", true, 7, "",  "full",  null);
        addField(fields, "labels",       "labels",      true, 8, "",  "full",  null);

        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("tabKey", "main");
        tab.put("label", "Main");
        tab.put("fields", fields);

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("tabs", List.of(tab));
        return layout;
    }

    private void addField(List<Map<String, Object>> fields, String key, String type,
                          boolean system, int order, String defaultValue, String width,
                          List<String> fieldOptions) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldKey", key);
        field.put("fieldType", type);
        field.put("isSystem", system);
        field.put("displayOrder", order);
        field.put("defaultValue", defaultValue);
        field.put("width", width);
        if (fieldOptions != null && !fieldOptions.isEmpty()) {
            field.put("fieldOptions", fieldOptions);
        }
        fields.add(field);
    }

    private TemplateWithLayoutDto toWithLayoutDto(TemplateEntity t, TemplateVersionEntity v) {
        TemplateWithLayoutDto dto = new TemplateWithLayoutDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setAiPurpose(t.getAiPurpose());
        dto.setCurrentVersionNumber(v.getVersionNumber());
        dto.setCurrentVersionId(v.getId());
        dto.setLayout(maskWorkflowSecrets(enrichLayoutWithAdminOnly(v.getLayout())));
        dto.setDefault(t.isDefault());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setUpdatedAt(t.getUpdatedAt());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichLayoutWithAdminOnly(Map<String, Object> layout) {
        if (layout == null) return null;

        Map<String, Boolean> adminOnlyMap = new HashMap<>();
        Map<String, List<String>> fieldOptionsMap = new HashMap<>();
        for (FieldDefinitionsEntity f : fieldDefinitionsService.getCustomFields("ticket")) {
            adminOnlyMap.put(f.getFieldKey(), f.isAdminOnly());
            if (f.getFieldOptions() != null && !f.getFieldOptions().isEmpty()) {
                fieldOptionsMap.put(f.getFieldKey(), f.getFieldOptions());
            }
        }

        Object tabs = layout.get("tabs");
        if (!(tabs instanceof List<?>)) return layout;

        List<Object> newTabs = new ArrayList<>();
        for (Object tabObj : (List<?>) tabs) {
            if (!(tabObj instanceof Map)) { newTabs.add(tabObj); continue; }
            Map<String, Object> tab = new LinkedHashMap<>((Map<String, Object>) tabObj);
            Object fields = tab.get("fields");
            if (fields instanceof List<?>) {
                List<Object> newFields = new ArrayList<>();
                for (Object fObj : (List<?>) fields) {
                    if (!(fObj instanceof Map)) { newFields.add(fObj); continue; }
                    Map<String, Object> field = new LinkedHashMap<>((Map<String, Object>) fObj);
                    String fKey = (String) field.get("fieldKey");
                    if (fKey != null) {
                        field.put("isAdminOnly", adminOnlyMap.getOrDefault(fKey, false));
                        if (fieldOptionsMap.containsKey(fKey)) {
                            field.put("fieldOptions", fieldOptionsMap.get(fKey));
                        }
                    }
                    newFields.add(field);
                }
                tab.put("fields", newFields);
            }
            newTabs.add(tab);
        }
        Map<String, Object> result = new LinkedHashMap<>(layout);
        result.put("tabs", newTabs);
        return result;
    }

    // ── external_api / mcp_tool secret handling (FEAT-06 Phase 4) ───────────────
    // typeConfig.calls[].auth may carry "token"/"username"/"password" (plaintext, admin input) which
    // must never be persisted as-is and never round-tripped back to any client — encrypted at rest
    // via AesEncryptionUtils (matches EmailMailboxEntity's OAuth/SMTP secret precedent, deliberately
    // NOT AiSettingsEntity.apiKey's plaintext-storage anti-pattern). GET /templates/{id} is callable
    // by any authenticated user (needed to render ticket forms for end users), so masking applies
    // regardless of the caller's permission level.

    // "Auth slot" = one secret-bearing auth block on a node, identified by a stable slotId so saves
    // can carry-forward/encrypt/mask it consistently. external_api has one slot PER CALL (slotId =
    // that call's own "id"); mcp_tool has exactly one slot for the whole node (its single shared
    // server connection) — given a fixed sentinel slotId since there's no per-call id to key off.
    private static final String MCP_NODE_AUTH_SLOT_ID = "__mcp_node_auth__";

    /** Mutates layout in place — encrypts any plaintext auth secrets, strips the plaintext keys. Safe to call on a freshly-deserialized request DTO (not JPA-managed). */
    private void encryptWorkflowSecrets(Map<String, Object> layout) {
        forEachAuthSlot(layout, (slotId, auth) -> {
            encryptSecretField(auth, "token", "tokenEnc");
            encryptSecretField(auth, "username", "usernameEnc");
            encryptSecretField(auth, "password", "passwordEnc");
        });
    }

    private void encryptSecretField(Map<String, Object> auth, String plainKey, String encKey) {
        if (!auth.containsKey(plainKey)) return; // untouched — carryForwardWorkflowSecrets already handled it
        Object plain = auth.remove(plainKey);
        if (plain instanceof String s && !s.isBlank()) {
            auth.put(encKey, aes.encrypt(s));
        } else {
            auth.remove(encKey); // explicit clear (blank/null value)
        }
    }

    /** Mutates newLayout in place — for any auth slot missing a plaintext secret key entirely (admin didn't touch it), copies the previously-encrypted value across by matching node id + slot id, so an unrelated template edit doesn't silently wipe a configured credential. */
    private void carryForwardWorkflowSecrets(Map<String, Object> newLayout, Map<String, Object> oldLayout) {
        if (oldLayout == null) return;
        Map<String, Map<String, Map<String, Object>>> oldAuthByNodeAndSlot = new HashMap<>();
        forEachWorkflowNode(oldLayout, node -> {
            String nodeId = str(node.get("id"));
            if (nodeId == null) return;
            forEachAuthSlotOfNode(node, (slotId, auth) ->
                    oldAuthByNodeAndSlot.computeIfAbsent(nodeId, k -> new HashMap<>()).put(slotId, auth));
        });
        if (oldAuthByNodeAndSlot.isEmpty()) return;

        forEachWorkflowNode(newLayout, node -> {
            String nodeId = str(node.get("id"));
            Map<String, Map<String, Object>> oldSlots = nodeId != null ? oldAuthByNodeAndSlot.get(nodeId) : null;
            if (oldSlots == null) return;
            forEachAuthSlotOfNode(node, (slotId, auth) -> {
                Map<String, Object> oldAuth = oldSlots.get(slotId);
                if (oldAuth == null) return;
                carryIfUntouched(auth, oldAuth, "token", "tokenEnc");
                carryIfUntouched(auth, oldAuth, "username", "usernameEnc");
                carryIfUntouched(auth, oldAuth, "password", "passwordEnc");
            });
        });
    }

    private void carryIfUntouched(Map<String, Object> newAuth, Map<String, Object> oldAuth, String plainKey, String encKey) {
        if (newAuth.containsKey(plainKey)) return; // explicit admin action this save — never override it
        Object oldEnc = oldAuth.get(encKey);
        if (oldEnc instanceof String s && !s.isBlank()) newAuth.put(encKey, s);
    }

    /**
     * A real regression found live, introduced by ActionItemLibraryService's own secret-masking
     * fix: "Add from Library" (WorkflowDesignerModal.tsx) copies a library entry's typeConfig by
     * value into a brand-new template node — but that entry was fetched via a GET that (correctly)
     * never sends real ciphertext to the browser, only a "hasToken"-style flag. Before that
     * masking existed, the library's GET happened to leak the real plaintext token, which is what
     * let the copy-then-save flow accidentally work; now that hole is closed, a freshly-added
     * library node has "hasToken: true" but no actual secret anywhere, and the token is silently
     * lost forever (carryForwardWorkflowSecrets above can't help — it matches by node id, and this
     * is a brand-new node id never seen in any prior template version). Since "Add from Library"
     * preserves the library entry's own call id unchanged (confirmed live — a copied external_api
     * call keeps its source call's UUID), this closes the gap server-side, never routing ciphertext
     * through the browser: for any auth slot still missing a real secret after the above, look up
     * every action_item_library row for a call with the SAME id and carry its real *Enc value
     * across. mcp_tool's single node-level auth has no per-call id to match against, so it isn't
     * covered by this fallback — only relevant if that gets its own "Add from Library" path later.
     */
    @SuppressWarnings("unchecked")
    private void carryForwardSecretsFromLibrary(Map<String, Object> newLayout) {
        Map<String, Map<String, Object>> libraryAuthByCallId = null;
        for (com.turbotikects.turbotikectsserver.entitys.ActionItemLibraryEntity entry : actionItemLibraryRepo.findAll()) {
            Map<String, Object> tc = entry.getTypeConfig();
            if (tc == null || !(tc.get("calls") instanceof List<?> calls)) continue;
            for (Object c : calls) {
                if (!(c instanceof Map<?, ?> call) || !(call.get("id") instanceof String cid) || !(call.get("auth") instanceof Map)) continue;
                if (libraryAuthByCallId == null) libraryAuthByCallId = new HashMap<>();
                libraryAuthByCallId.put(cid, (Map<String, Object>) call.get("auth"));
            }
        }
        if (libraryAuthByCallId == null) return;
        Map<String, Map<String, Object>> finalLibraryAuthByCallId = libraryAuthByCallId;
        forEachAuthSlot(newLayout, (slotId, auth) -> {
            Map<String, Object> libAuth = finalLibraryAuthByCallId.get(slotId);
            if (libAuth == null) return;
            carryIfStillMissing(auth, libAuth, "token", "tokenEnc");
            carryIfStillMissing(auth, libAuth, "username", "usernameEnc");
            carryIfStillMissing(auth, libAuth, "password", "passwordEnc");
        });
    }

    /** Like carryIfUntouched, but only fills a gap carryForwardWorkflowSecrets left open (a genuinely real *Enc value already present wins — this never overwrites one). */
    private void carryIfStillMissing(Map<String, Object> auth, Map<String, Object> libAuth, String plainKey, String encKey) {
        if (auth.containsKey(plainKey)) return; // explicit admin action this save — never override it
        if (auth.get(encKey) instanceof String s && !s.isBlank()) return; // already resolved (carried forward from the old template version, or a fresh encrypt)
        Object libEnc = libAuth.get(encKey);
        if (libEnc instanceof String s && !s.isBlank()) auth.put(encKey, s);
    }

    /** Returns a deep copy of layout with every auth slot's secrets replaced by "has"-prefixed boolean flags — never exposes ciphertext (defense-in-depth: this endpoint is reachable by any authenticated user, not just admins) or plaintext to any client. */
    private Map<String, Object> maskWorkflowSecrets(Map<String, Object> layout) {
        if (layout == null) return null;
        Map<String, Object> copy = new ObjectMapper().convertValue(layout, new TypeReference<Map<String, Object>>() {});
        forEachAuthSlot(copy, (slotId, auth) -> {
            maskSecretField(auth, "tokenEnc", "hasToken");
            maskSecretField(auth, "usernameEnc", "hasUsername");
            maskSecretField(auth, "passwordEnc", "hasPassword");
        });
        return copy;
    }

    private void maskSecretField(Map<String, Object> auth, String encKey, String hasFlagKey) {
        Object enc = auth.remove(encKey);
        auth.put(hasFlagKey, enc instanceof String s && !s.isBlank());
    }

    private interface AuthSlotConsumer { void accept(String slotId, Map<String, Object> auth); }

    private void forEachAuthSlot(Map<String, Object> layout, AuthSlotConsumer fn) {
        forEachWorkflowNode(layout, node -> forEachAuthSlotOfNode(node, fn));
    }

    @SuppressWarnings("unchecked")
    private void forEachAuthSlotOfNode(Map<String, Object> node, AuthSlotConsumer fn) {
        if ("mcp_tool".equals(node.get("type"))) {
            Object typeConfigObj = node.get("typeConfig");
            if (typeConfigObj instanceof Map) {
                Object authObj = ((Map<String, Object>) typeConfigObj).get("auth");
                if (authObj instanceof Map) fn.accept(MCP_NODE_AUTH_SLOT_ID, (Map<String, Object>) authObj);
            }
            return;
        }
        for (Map<String, Object> call : callsOfNode(node)) {
            String callId = str(call.get("id"));
            Object authObj = call.get("auth");
            if (callId != null && authObj instanceof Map) fn.accept(callId, (Map<String, Object>) authObj);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callsOfNode(Map<String, Object> node) {
        Object typeConfigObj = node.get("typeConfig");
        if (!(typeConfigObj instanceof Map)) return List.of();
        Object callsObj = ((Map<String, Object>) typeConfigObj).get("calls");
        if (!(callsObj instanceof List)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object c : (List<?>) callsObj) if (c instanceof Map) result.add((Map<String, Object>) c);
        return result;
    }

    /** Mirrors WorkflowService.extractWorkflowNodes' traversal shape (same layout JSON structure) — walks every node of every 'workflow'-type field across every tab. */
    @SuppressWarnings("unchecked")
    private void forEachWorkflowNode(Map<String, Object> layout, Consumer<Map<String, Object>> fn) {
        if (layout == null) return;
        Object tabsObj = layout.get("tabs");
        if (!(tabsObj instanceof List)) return;
        for (Object tabObj : (List<?>) tabsObj) {
            if (!(tabObj instanceof Map)) continue;
            Object fieldsObj = ((Map<String, Object>) tabObj).get("fields");
            if (!(fieldsObj instanceof List)) continue;
            for (Object fieldObj : (List<?>) fieldsObj) {
                if (!(fieldObj instanceof Map)) continue;
                Map<String, Object> field = (Map<String, Object>) fieldObj;
                if (!"workflow".equals(field.get("fieldType"))) continue;
                Object fcObj = field.get("fieldConfig");
                if (!(fcObj instanceof Map)) continue;
                Object nodesObj = ((Map<String, Object>) fcObj).get("nodes");
                if (!(nodesObj instanceof List)) continue;
                for (Object nodeObj : (List<?>) nodesObj) {
                    if (nodeObj instanceof Map) fn.accept((Map<String, Object>) nodeObj);
                }
            }
        }
    }

    private String str(Object o) {
        return o instanceof String s ? s : null;
    }
}
