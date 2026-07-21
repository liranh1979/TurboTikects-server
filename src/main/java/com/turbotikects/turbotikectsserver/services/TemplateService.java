package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import com.turbotikects.turbotikectsserver.repositorys.TemplateRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;

@Service
public class TemplateService {

    private final TemplateRepository templateRepo;
    private final TemplateVersionRepository versionRepo;
    private final FieldDefinitionsService fieldDefinitionsService;
    private final AiSettingsService aiSettingsService;
    private final AesEncryptionUtils aes;

    public TemplateService(TemplateRepository templateRepo,
                           TemplateVersionRepository versionRepo,
                           FieldDefinitionsService fieldDefinitionsService,
                           AiSettingsService aiSettingsService,
                           AesEncryptionUtils aes) {
        this.templateRepo = templateRepo;
        this.versionRepo = versionRepo;
        this.fieldDefinitionsService = fieldDefinitionsService;
        this.aiSettingsService = aiSettingsService;
        this.aes = aes;
    }

    public List<TemplateSummaryDto> getAll() {
        List<TemplateEntity> templates = templateRepo.findAll();
        List<TemplateSummaryDto> result = new ArrayList<>();
        for (TemplateEntity t : templates) {
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

    /**
     * FEAT-06 Phase 5 — AI Workflow Builder. Given pasted API docs + a freeform description of the
     * desired behavior, asks the active LLM to draft an external_api typeConfig (calls +
     * fieldMappings, matching ExternalApiActionExecutor's exact shape from Phase 4). The draft is
     * NEVER auto-applied to any template — it's returned for the admin to review/edit through the
     * same structured ExternalApiCallsEditor/ExternalApiFieldMappingsEditor Phase 4 already built,
     * then saved through the normal PUT /templates/{id} path (which is also where real secret
     * values get entered and encrypted — the LLM is never given, and never asked to invent, an
     * actual credential value). Mirrors every other AI-assisted feature in this codebase (CSAT/SLA/
     * dashboard insights, aiSuggestLayout above): AI proposes structured data, a human confirms —
     * consistent with this whole feature's deliberate choice to keep the external_api/mcp_tool
     * executors purely declarative/interpreted rather than AI-generated executable code.
     */
    public Map<String, Object> aiSuggestWorkflowAction(AiWorkflowActionDraftRequestDto dto)
            throws IOException, URISyntaxException, InterruptedException {
        if (dto.getDocumentation() == null || dto.getDocumentation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentation is required");
        }
        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        String fieldList = dto.getTicketFieldKeys() == null || dto.getTicketFieldKeys().isEmpty()
                ? "(none configured)" : String.join(", ", dto.getTicketFieldKeys());
        String workflowFieldList = workflowFieldsJson(dto.getWorkflowFields());

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You design HTTP API integration configs for a workflow automation system. Given API \
                documentation and a description of what the admin wants to happen, produce a JSON \
                object with EXACTLY this shape (no markdown, no explanation, ONLY the JSON):
                {
                  "calls": [
                    {
                      "name": "short_snake_case_name",
                      "method": "GET"|"POST"|"PUT"|"PATCH"|"DELETE",
                      "urlTemplate": "https://... using {{placeholder}} for variable parts",
                      "headers": [{"key": "Header-Name", "valueTemplate": "value or {{placeholder}}"}],
                      "auth": {"type": "none"|"bearer"|"api_key"|"basic", "headerName": "only for api_key type"},
                      "bodyTemplate": "JSON string body using {{placeholder}} for variable parts, or empty string for no body",
                      "responseCaptures": [{"name": "camelCaseName", "jsonPath": "$.path.to.value"}]
                    }
                  ],
                  "fieldMappings": {
                    "request": [{"placeholder": "name used in templates above", "ticketField": "ticket.<field> or this.<workflow field>"}],
                    "response": [{"captureName": "must match a name in some call's responseCaptures", "target": "ticket.<field> or this.<workflow field>"}]
                  },
                  "missingWorkflowFields": [
                    {"suggestedFieldKey": "snake_case_key", "suggestedLabel": "Human Label", "suggestedFieldType": "text"|"number"|"date"|"checkbox"}
                  ]
                }
                Rules:
                - NEVER include a real token/username/password value anywhere in your output — the \
                  admin enters real credentials separately afterward in a secure form you don't see. \
                  Only set "auth.type" (and "headerName" for api_key) based on what the docs describe.
                - If you set "auth.type" to anything other than "none", do NOT also add a manual \
                  "Authorization" header — the system adds that header automatically from the auth \
                  config, and a duplicate manual one will send the wrong value.
                - Placeholder names and responseCaptures names MUST contain ONLY lowercase letters, \
                  numbers, and underscores — no dots, dashes, or spaces. Correct: "employee_name". \
                  Wrong: "ticket.title", "employee-name". This applies everywhere a name is used: \
                  {{placeholder}} in templates, fieldMappings.request[].placeholder, \
                  responseCaptures[].name, and fieldMappings.response[].captureName.
                - Only use placeholders in fieldMappings.request that are actually referenced via \
                  {{...}} in a call's urlTemplate/headers/bodyTemplate.
                - Both fieldMappings.request[].ticketField and fieldMappings.response[].target MUST \
                  start with either "ticket." (a ticket field — request reads it, response writes it, \
                  from the provided list of available ticket fields) or "this." (this action item's \
                  own data, from the provided list of available workflow fields, each given with its \
                  "type") — never a bare field name like "description" with no prefix, it will \
                  silently be ignored.
                - MANDATORY CHECK for every single "this.<key>" you write, with no exceptions — \
                  including response captures you're about to store, even though a capture's own \
                  name might look field-ready as-is: does <key> EXACTLY match a key already in the \
                  provided workflow fields list, AND is that field's "type" a genuinely good fit for \
                  what you're reading or writing here? If YES, use that exact key. If NO — including \
                  when nothing in the list is close, or a name-only match has the wrong type — you \
                  MUST do BOTH of the following, never just the "this.<key>" reference alone: (1) \
                  still write "this.<key>" at that location (invent a new snake_case key if needed — \
                  a captured value's own camelCase/existing name is NOT automatically a valid key, \
                  convert it), AND (2) add exactly one entry for that same key to the top-level \
                  "missingWorkflowFields" array. It is a mistake to reference a "this.<key>" that is \
                  neither in the provided workflow fields list NOR declared in "missingWorkflowFields" \
                  — every single one must be one or the other, no silent third option. \
                  "suggestedFieldType" MUST be exactly one of "text", "number", "date", or "checkbox" \
                  — never anything else (no "combobox", "assignee", etc. — those need configuration \
                  you can't safely infer).
                - A "this.<key>" reference used as a request source (fieldMappings.request[].ticketField) \
                  means a value expected to already be known — filled in by a human, or captured by an \
                  earlier call in this same sequence — before this call runs; if you invent a new key \
                  here, it becomes a fillable input the admin can expose on the item afterward. A \
                  "this.<key>" reference used as a response target (fieldMappings.response[].target) \
                  means a value this call sequence itself produces and should persist; if you invent a \
                  new key here, it becomes a place to store that output.
                - If the docs describe a multi-step flow (e.g. authenticate then call), create \
                  multiple calls in order, with a later call referencing an earlier call's \
                  responseCapture directly as {{captureName}} (no extra namespacing needed).
                - Keep it minimal and correct rather than speculative — omit fields you're not \
                  confident about rather than guessing.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Available ticket fields: " + fieldList +
                "\n\nAvailable workflow fields: " + workflowFieldList +
                "\n\nWhat this action should do: " + (dto.getIntent() == null ? "" : dto.getIntent()) +
                "\n\nAPI documentation:\n" + dto.getDocumentation());

        String raw = aiSettingsService.sendLlmRequest(aiSettings, List.of(system, user));
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> draft;
        try {
            draft = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            // The model responded conversationally instead of with pure JSON (seen live with a
            // smaller local model given a large/unfamiliar tool schema) — surface this as a clear
            // 4xx instead of letting a raw JsonParseException bubble up as an unhandled 500.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — it may not have understood the request. Try a shorter/clearer intent, or a different AI provider/model.");
        }
        sanitizeDraft(draft, existingWorkflowFieldKeys(dto.getWorkflowFields()));
        return draft;
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

    /** Defense in depth: assigns real call ids/order server-side (never trust the LLM to invent stable, unique ones) and strips any secret-shaped key the model might have hallucinated despite being told not to — never let AI-generated output masquerade as a real stored credential. */
    @SuppressWarnings("unchecked")
    private void sanitizeDraft(Map<String, Object> draft, Set<String> existingWorkflowFieldKeys) {
        Set<String> validAuthTypes = Set.of("none", "bearer", "api_key", "basic");
        List<Map<String, Object>> calls = new ArrayList<>();
        if (draft.get("calls") instanceof List<?> list) {
            int order = 0;
            for (Object c : list) {
                if (!(c instanceof Map)) continue;
                Map<String, Object> call = new LinkedHashMap<>((Map<String, Object>) c);
                call.put("id", UUID.randomUUID().toString());
                call.put("order", order++);
                call.putIfAbsent("headers", new ArrayList<>());
                call.putIfAbsent("responseCaptures", new ArrayList<>());
                call.putIfAbsent("bodyTemplate", "");

                Map<String, Object> auth = call.get("auth") instanceof Map
                        ? new LinkedHashMap<>((Map<String, Object>) call.get("auth")) : new LinkedHashMap<>();
                auth.remove("token"); auth.remove("username"); auth.remove("password");
                auth.remove("tokenEnc"); auth.remove("usernameEnc"); auth.remove("passwordEnc");
                if (!(auth.get("type") instanceof String s) || !validAuthTypes.contains(s)) {
                    auth.put("type", "none");
                }
                call.put("auth", auth);
                calls.add(call);
            }
        }
        draft.put("calls", calls);

        Map<String, Object> fm = draft.get("fieldMappings") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) draft.get("fieldMappings")) : new LinkedHashMap<>();
        fm.putIfAbsent("request", new ArrayList<>());
        fm.putIfAbsent("response", new ArrayList<>());
        draft.put("fieldMappings", fm);

        draft.put("missingWorkflowFields", sanitizeMissingWorkflowFields(draft, existingWorkflowFieldKeys));
    }

    /**
     * FEAT-06 Phase 7 — AI Workflow Builder extended to MCP. Unlike aiSuggestWorkflowAction (which
     * has to infer an HTTP request shape from prose documentation), this is given the SERVER'S REAL
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
        String fieldList = dto.getTicketFieldKeys() == null || dto.getTicketFieldKeys().isEmpty()
                ? "(none configured)" : String.join(", ", dto.getTicketFieldKeys());
        String workflowFieldList = workflowFieldsJson(dto.getWorkflowFields());

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You configure calls to an MCP (Model Context Protocol) server's tools for a workflow \
                automation system. You are given the server's REAL tools with their exact JSON-schema \
                argument definitions — only use tool names and argument names that actually appear in \
                that list, never invent one. Produce a JSON object with EXACTLY this shape (no \
                markdown, no explanation, ONLY the JSON):
                {
                  "calls": [
                    {
                      "toolName": "must be one of the provided tools' exact name",
                      "argumentMappings": [
                        {"toolArgument": "must be a property key in that tool's inputSchema.properties", "ticketField": "ticket.<field> or this.<workflow field>"}
                      ],
                      "responseCaptures": [{"name": "camelCaseName", "resultPath": "$.text or $.someField"}]
                    }
                  ],
                  "fieldMappings": {
                    "response": [{"captureName": "must match a name in some call's responseCaptures", "target": "ticket.<field> or this.<workflow field>"}]
                  },
                  "missingWorkflowFields": [
                    {"suggestedFieldKey": "snake_case_key", "suggestedLabel": "Human Label", "suggestedFieldType": "text"|"number"|"date"|"checkbox"}
                  ]
                }
                Rules:
                - Only map arguments that appear in the chosen tool's inputSchema.properties; prefer
                  mapping every property listed in that schema's "required" array.
                - An argumentMapping entry has EITHER "ticketField" (reads from "ticket.<field>", one
                  of the available ticket fields, or "this.<workflow field>", one of the available
                  workflow fields, each given with its "type") OR "captureName" (reads a value captured
                  by an EARLIER call in this same sequence) — never both, and captureName may only
                  reference a responseCaptures name from a call that comes before it in the list.
                - MANDATORY CHECK for every single "this.<key>" you write, with no exceptions —
                  including response targets whose captureName might already look field-ready as-is:
                  does <key> EXACTLY match a key already in the provided workflow fields list, AND is
                  that field's "type" a genuinely good fit for what you're reading or writing here? If
                  YES, use that exact key. If NO — including when nothing in the list is close, or a
                  name-only match has the wrong type — you MUST do BOTH of the following, never just
                  the "this.<key>" reference alone: (1) still write "this.<key>" at that location
                  (invent a new snake_case key if needed — a captureName's own camelCase/existing
                  spelling is NOT automatically a valid key, convert it), AND (2) add exactly one
                  entry for that same key to the top-level "missingWorkflowFields" array. It is a
                  mistake to reference a "this.<key>" that is neither in the provided workflow fields
                  list NOR declared in "missingWorkflowFields" — every single one must be one or the
                  other, no silent third option. "suggestedFieldType" MUST be exactly one of "text",
                  "number", "date", or "checkbox" — never anything else.
                - A "this.<key>" reference used as an argument source (argumentMappings[].ticketField)
                  means a value expected to already be known — filled in by a human, or captured by an
                  earlier call — before this call runs; if you invent a new key here, it becomes a
                  fillable input the admin can expose on the item afterward. A "this.<key>" reference
                  used as a response target (fieldMappings.response[].target) means a value this call
                  sequence itself produces and should persist; if you invent a new key here, it becomes
                  a place to store that output.
                - responseCaptures[].resultPath is a JSONPath: use "$.text" for a tool that returns
                  plain text, or "$.fieldName" if the tool's outputSchema (if present) suggests
                  structured output.
                - Every fieldMappings.response[].target MUST start with either "ticket." (from the
                  available ticket fields) or "this." (from the available workflow fields) — never a
                  bare field name.
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
            // Same defense as aiSuggestWorkflowAction — a large/unfamiliar real tool schema (e.g. a
            // production MCP server with many complex tools) can make a smaller model respond
            // conversationally instead of with pure JSON; surface a clear 4xx, not a raw 500.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI did not return valid JSON — it may not have understood the request or the tool schema was too complex for it. Try a shorter/clearer intent, a smaller set of tools, or a different AI provider/model.");
        }
        sanitizeMcpDraft(draft, existingWorkflowFieldKeys(dto.getWorkflowFields()));
        return draft;
    }

    /** Defense in depth, mirrors sanitizeDraft: never trusts the LLM for stable/unique call ids/ordering, guarantees calls/fieldMappings.response are always present arrays. mcp_tool calls have no per-call auth to strip (the server connection's single auth lives at the node level, entered by the admin directly — never asked of the LLM at all here). */
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
                call.putIfAbsent("responseCaptures", new ArrayList<>());
                calls.add(call);
            }
        }
        draft.put("calls", calls);

        Map<String, Object> fm = draft.get("fieldMappings") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) draft.get("fieldMappings")) : new LinkedHashMap<>();
        fm.putIfAbsent("response", new ArrayList<>());
        draft.put("fieldMappings", fm);

        draft.put("missingWorkflowFields", sanitizeMissingWorkflowFields(draft, existingWorkflowFieldKeys));
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
