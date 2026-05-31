package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LdapMappingService {

    private final LdapService ldapService;
    private final AiSettingsService aiSettingsService;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final DynamicTranslationsRepository dynamicTranslationsRepository;
    private final ObjectMapper objectMapper;

    public LdapMappingService(LdapService ldapService,
                               AiSettingsService aiSettingsService,
                               FieldDefinitionsRepository fieldDefinitionsRepository,
                               DynamicTranslationsRepository dynamicTranslationsRepository) {
        this.ldapService = ldapService;
        this.aiSettingsService = aiSettingsService;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.dynamicTranslationsRepository = dynamicTranslationsRepository;
        this.objectMapper = new ObjectMapper();
    }

    public LdapAiMappingResultDto suggestMappings(Long configId, String entityType) {
        // 1. Fetch sample from LDAP
        Map<String, String> sampleAttributes;
        try {
            LdapSampleDto sample = "group".equals(entityType)
                    ? ldapService.fetchSampleGroup(configId)
                    : ldapService.fetchSampleUser(configId);
            sampleAttributes = sample.getAttributes();
        } catch (Exception e) {
            return errorResult("Failed to fetch LDAP sample: " + e.getMessage());
        }

        if (sampleAttributes == null || sampleAttributes.isEmpty()) {
            return errorResult("No LDAP sample data found for entity type: " + entityType);
        }

        // 2. Load custom field definitions + English labels
        List<FieldDefinitionsEntity> fields =
                fieldDefinitionsRepository.findByEntityTypeAndIsSystemFalseOrderByDisplayOrder(entityType);

        String translationType = entityType + "_fields";
        Map<String, String> labelMap = new HashMap<>();
        dynamicTranslationsRepository.findAllByLangCodeAndType("en", translationType)
                .ifPresent(list -> list.forEach(t -> labelMap.put(t.getTranslationKey(), t.getTranslatedText())));

        // 3. Build the system fields list (built-ins + custom)
        List<Map<String, String>> systemFields = new ArrayList<>();
        if ("group".equals(entityType)) {
            systemFields.add(Map.of("key", "display_name", "label", "Group Name", "type", "text"));
        } else {
            systemFields.add(Map.of("key", "username", "label", "Login Username", "type", "text"));
            systemFields.add(Map.of("key", "display_name", "label", "Display Name", "type", "text"));
        }
        for (FieldDefinitionsEntity f : fields) {
            String label = labelMap.getOrDefault(f.getFieldKey(), f.getFieldKey());
            systemFields.add(Map.of("key", f.getFieldKey(), "label", label, "type", f.getFieldType()));
        }

        // 4. Require active AI
        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) {
            return errorResult("No active AI provider configured. Go to Settings → AI Manager to configure one.");
        }

        // 5. Build prompt and call LLM
        try {
            String ldapAttributesJson = objectMapper.writeValueAsString(sampleAttributes);
            String systemFieldsJson = objectMapper.writeValueAsString(systemFields);
            String prompt = buildPrompt(ldapAttributesJson, systemFieldsJson, entityType);

            List<LlmStructure> messages = new ArrayList<>();
            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("You are a data integration assistant. Respond only with valid JSON, no markdown, no explanation.");
            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent(prompt);
            messages.add(system);
            messages.add(user);

            String rawResponse = aiSettingsService.sendLlmRequest(ai, messages);
            return parseResponse(rawResponse, sampleAttributes);

        } catch (Exception e) {
            return errorResult("AI mapping failed: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildPrompt(String ldapAttributesJson, String systemFieldsJson, String entityType) {
        return "You are a data integration assistant helping map LDAP directory attributes to application fields.\n\n" +
                "LDAP ATTRIBUTES from a sample " + entityType + " record:\n" +
                ldapAttributesJson + "\n\n" +
                "APPLICATION FIELDS available for entity type \"" + entityType + "\":\n" +
                systemFieldsJson + "\n\n" +
                "TASK:\n" +
                "1. For each LDAP attribute, find the best matching application field.\n" +
                "   - If confident it matches, include it in \"mappings\" with confidence: high/medium/low.\n" +
                "   - If there is no suitable field, include it in \"missingFields\" with a suggested new field definition.\n" +
                "2. Skip LDAP operational/technical attributes: objectClass, objectGUID, objectSid, " +
                "uSNCreated, uSNChanged, whenCreated, whenChanged, instanceType, distinguishedName, " +
                "memberOf, pwdLastSet, accountExpires, logonCount, badPwdCount, badPasswordTime, " +
                "lastLogon, lastLogonTimestamp, userAccountControl.\n\n" +
                "Return ONLY valid JSON in this exact format:\n" +
                "{\n" +
                "  \"mappings\": [\n" +
                "    {\n" +
                "      \"ldapAttribute\": \"sAMAccountName\",\n" +
                "      \"systemFieldKey\": \"username\",\n" +
                "      \"confidence\": \"high\",\n" +
                "      \"reasoning\": \"sAMAccountName is the Windows login name\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"missingFields\": [\n" +
                "    {\n" +
                "      \"ldapAttribute\": \"employeeID\",\n" +
                "      \"suggestedFieldKey\": \"employee_id\",\n" +
                "      \"suggestedLabel\": \"Employee ID\",\n" +
                "      \"suggestedFieldType\": \"text\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    private LdapAiMappingResultDto parseResponse(String rawResponse, Map<String, String> sampleAttributes) {
        try {
            // Strip markdown code fences if present
            String cleaned = rawResponse
                    .replaceAll("(?s)```[a-zA-Z]*\\n?", "")
                    .replace("```", "")
                    .trim();

            LdapAiMappingResultDto result = objectMapper.readValue(cleaned, LdapAiMappingResultDto.class);

            // Back-fill sample values into each mapping suggestion
            if (result.getMappings() != null) {
                for (LdapMappingSuggestionDto m : result.getMappings()) {
                    m.setLdapSampleValue(sampleAttributes.getOrDefault(m.getLdapAttribute(), ""));
                }
            } else {
                result.setMappings(new ArrayList<>());
            }

            if (result.getMissingFields() == null) {
                result.setMissingFields(new ArrayList<>());
            }

            result.setHasUnmappedAttributes(!result.getMissingFields().isEmpty());
            return result;

        } catch (Exception e) {
            return errorResult("Failed to parse AI response: " + e.getMessage() + ". Raw: " + rawResponse);
        }
    }

    private LdapAiMappingResultDto errorResult(String message) {
        LdapAiMappingResultDto result = new LdapAiMappingResultDto();
        result.setMappings(new ArrayList<>());
        result.setMissingFields(new ArrayList<>());
        result.setHasUnmappedAttributes(false);
        result.setError(message);
        return result;
    }
}
