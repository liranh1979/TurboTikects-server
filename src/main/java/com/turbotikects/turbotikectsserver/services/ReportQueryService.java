package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.ReportFieldDto;
import com.turbotikects.turbotikectsserver.dto.ReportPreviewResultDto;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Owns the field catalog, the AI query-builder agent, and query execution/preview. See
 * V2/repoets/feat-05-02-ai-query-agent.html for the design — in particular, the AI here only
 * ever proposes a whitelisted JSON selection; ReportQueryCompiler is the only thing that ever
 * turns it into a real query, and every AI-proposed fieldKey is validated against the real
 * catalog before use.
 */
@Slf4j
@Service
public class ReportQueryService {

    private final TicketRepository ticketRepo;
    private final FieldDefinitionsService fieldDefinitionsService;
    private final AiSettingsService aiSettingsService;
    private final UserRepository userRepo;
    private final GroupRepository groupRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportQueryService(TicketRepository ticketRepo,
                               FieldDefinitionsService fieldDefinitionsService,
                               AiSettingsService aiSettingsService,
                               UserRepository userRepo,
                               GroupRepository groupRepo) {
        this.ticketRepo = ticketRepo;
        this.fieldDefinitionsService = fieldDefinitionsService;
        this.aiSettingsService = aiSettingsService;
        this.userRepo = userRepo;
        this.groupRepo = groupRepo;
    }

    // ── Field catalog ───────────────────────────────────────────────────────────

    public List<ReportFieldDto> getTicketFieldCatalog() {
        List<ReportFieldDto> result = new ArrayList<>();
        for (Map.Entry<String, String> e : ReportQueryCompiler.SYSTEM_FIELDS.entrySet()) {
            result.add(new ReportFieldDto(e.getKey(), systemFieldLabel(e.getKey()), e.getValue(), false));
        }
        // Only genuine custom fields (values live in tickets.ticket_data) — field_definitions
        // rows with isSystem=true for entityType='ticket' describe metadata (e.g. status's
        // configurable options) about a real column, not a ticketData key, so they're excluded
        // here in favor of this class's own fixed SYSTEM_FIELDS whitelist above.
        Map<String, String> labels = fieldDefinitionsService.getFieldTranslations("en", "ticket_fields");
        for (FieldDefinitionsEntity f : fieldDefinitionsService.getCustomFields("ticket")) {
            if (f.isSystem()) continue;
            result.add(new ReportFieldDto(f.getFieldKey(),
                    labels.getOrDefault(f.getFieldKey(), f.getFieldKey()), f.getFieldType(), true));
        }
        return result;
    }

    private static String systemFieldLabel(String key) {
        return switch (key) {
            case "id" -> "Ticket ID";
            case "title" -> "Title";
            case "description" -> "Description";
            case "status" -> "Status";
            case "priority" -> "Priority";
            case "sourceType" -> "Source";
            case "responsibleUserId" -> "Assigned Agent";
            case "responsibleGroupId" -> "Assigned Group";
            case "requestUserId" -> "Requester";
            case "acceleration" -> "Acceleration";
            case "createdAt" -> "Created At";
            case "updatedAt" -> "Updated At";
            default -> key;
        };
    }

    // ── Query execution ─────────────────────────────────────────────────────────

    private static final int PREVIEW_LIMIT = 50;

    /** Authoritative matching set for a query_spec's conditions — used by preview, manual Test
     * runs, and scheduled runs alike, so all three can never disagree about what "matches" means. */
    public List<TicketEntity> findMatching(Map<String, Object> conditions) {
        Specification<TicketEntity> spec = ReportQueryCompiler.toSpecification(conditions);
        List<TicketEntity> candidates = ticketRepo.findAll(spec);
        return candidates.stream()
                .filter(t -> ReportQueryCompiler.matches(conditions, t, t.getTicketData()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> rowFor(TicketEntity t, List<String> selectedFields) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String field : selectedFields) {
            Object val = ReportQueryCompiler.isSystemField(field)
                    ? ReportQueryCompiler.getSystemFieldValue(t, field)
                    : (t.getTicketData() == null ? null : t.getTicketData().get(field));
            row.put(field, resolveDisplayValue(field, val));
        }
        return row;
    }

    /** responsibleUserId/requestUserId/responsibleGroupId are stored (and filtered on) as raw
     * IDs, but a report reader wants to see who, not a number — resolve to a display name for
     * output only. Filtering (ReportQueryCompiler) is untouched and still compares raw IDs. */
    private Object resolveDisplayValue(String field, Object rawValue) {
        if (rawValue == null) return null;
        return switch (field) {
            case "responsibleUserId", "requestUserId" -> {
                Long id = toLongId(rawValue);
                yield id == null ? rawValue : userRepo.findById(id).<Object>map(UserEntity::getDisplayName).orElse(rawValue);
            }
            case "responsibleGroupId" -> {
                Long id = toLongId(rawValue);
                yield id == null ? rawValue : groupRepo.findById(id).<Object>map(GroupEntity::getDisplayName).orElse(rawValue);
            }
            default -> rawValue;
        };
    }

    private static Long toLongId(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (Exception e) { return null; }
    }

    public ReportPreviewResultDto preview(List<String> selectedFields, Map<String, Object> conditions) {
        List<TicketEntity> matched = findMatching(conditions);
        ReportPreviewResultDto result = new ReportPreviewResultDto();
        result.setSelectedFields(selectedFields);
        result.setConditions(conditions);
        result.setMatchCount(matched.size());
        result.setPreviewRows(matched.stream().limit(PREVIEW_LIMIT)
                .map(t -> rowFor(t, selectedFields)).collect(Collectors.toList()));
        return result;
    }

    // ── AI query-builder agent ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public ReportPreviewResultDto aiBuildQuery(String prompt) {
        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active AI provider is configured.");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe the report you want first.");
        }

        List<ReportFieldDto> catalog = getTicketFieldCatalog();
        StringBuilder fieldList = new StringBuilder();
        for (ReportFieldDto f : catalog) {
            fieldList.append("- fieldKey: ").append(f.getFieldKey())
                    .append(", fieldType: ").append(f.getFieldType())
                    .append(", label: ").append(f.getLabel())
                    .append(", isCustom: ").append(f.isCustom())
                    .append('\n');
        }

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You build report queries for a ticketing system over a known field catalog only \
                — you never write SQL. Given the admin's plain-English description of a report, \
                propose which fields to show and what conditions to filter by, using ONLY fields \
                from the catalog below (fieldKey must match exactly, case-sensitive).

                Return ONLY a valid JSON object, no markdown, no explanation, matching exactly this \
                shape: {"selectedFields": [string, ...], "conditions": {"combinator": "AND"|"OR", \
                "conditions": [{"field": string, "fieldType": string, "operator": string, "value": any, \
                "isCustom": boolean} | {"combinator": "AND"|"OR", "conditions": [...]}]}}.

                Valid operators by fieldType: text -> equals, not_equals, contains, not_contains; \
                combobox -> equals, not_equals; number -> equals, not_equals, lt, gt; \
                date -> older_than, newer_than, is_between (value is [fromISODate, toISODate] for \
                is_between, otherwise a single ISO date/time string). Set isCustom to true only for \
                a field marked isCustom: true in the catalog below. If nothing meaningful can be \
                filtered, return conditions as {"combinator":"AND","conditions":[]}. Always include \
                at least 3 relevant selectedFields.

                Field catalog:
                """ + fieldList);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent(prompt);

        try {
            String raw = aiSettingsService.sendLlmRequest(ai, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            Map<String, Object> parsed = mapper.readValue(cleaned, new TypeReference<>() {});

            List<String> selectedFields = new ArrayList<>(
                    (List<String>) parsed.getOrDefault("selectedFields", List.of()));
            Map<String, Object> conditions = (Map<String, Object>) parsed.getOrDefault("conditions", Map.of());

            // Whitelist validation — the load-bearing safety step. Reject anything the AI proposed
            // that isn't a real field in the catalog, rather than trusting it blindly.
            Set<String> validKeys = catalog.stream().map(ReportFieldDto::getFieldKey).collect(Collectors.toSet());
            selectedFields = selectedFields.stream().filter(validKeys::contains).collect(Collectors.toList());
            conditions = stripUnknownFields(conditions, validKeys);

            if (selectedFields.isEmpty()) {
                selectedFields = new ArrayList<>(List.of("id", "title", "status", "priority", "createdAt"));
            }

            return preview(selectedFields, conditions);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ReportQueryService] AI query build failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI did not return a valid report query. Try rephrasing, or build it manually.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stripUnknownFields(Map<String, Object> node, Set<String> validKeys) {
        if (node == null) return Map.of();
        if (node.containsKey("combinator")) {
            List<Object> rawChildren = (List<Object>) node.getOrDefault("conditions", List.of());
            List<Map<String, Object>> cleaned = new ArrayList<>();
            for (Object childObj : rawChildren) {
                if (!(childObj instanceof Map)) continue;
                Map<String, Object> cleanedChild = stripUnknownFields((Map<String, Object>) childObj, validKeys);
                if (!cleanedChild.isEmpty()) cleaned.add(cleanedChild);
            }
            if (cleaned.isEmpty()) return Map.of();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("combinator", node.getOrDefault("combinator", "AND"));
            result.put("conditions", cleaned);
            return result;
        }
        if (node.containsKey("field")) {
            Object fieldObj = node.get("field");
            if (fieldObj == null || !validKeys.contains(fieldObj.toString())) return Map.of();
            return node;
        }
        return Map.of();
    }
}
