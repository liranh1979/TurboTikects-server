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
public class AzureMappingService {

    private final AzureService azureService;
    private final AiSettingsService aiSettingsService;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final DynamicTranslationsRepository dynamicTranslationsRepository;
    private final ObjectMapper objectMapper;

    public AzureMappingService(AzureService azureService,
                                AiSettingsService aiSettingsService,
                                FieldDefinitionsRepository fieldDefinitionsRepository,
                                DynamicTranslationsRepository dynamicTranslationsRepository) {
        this.azureService = azureService;
        this.aiSettingsService = aiSettingsService;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.dynamicTranslationsRepository = dynamicTranslationsRepository;
        this.objectMapper = new ObjectMapper();
    }

    public AzureAiMappingResultDto suggestMappings(Long configId, String entityType) {
        // 1. Fetch sample from Azure AD
        Map<String, String> sampleAttributes;
        try {
            AzureSampleDto sample = "group".equals(entityType)
                    ? azureService.fetchSampleGroup(configId)
                    : azureService.fetchSampleUser(configId);
            if (sample.getError() != null) {
                return errorResult("Failed to fetch Azure sample: " + sample.getError());
            }
            sampleAttributes = sample.getAttributes();
        } catch (Exception e) {
            return errorResult("Failed to fetch Azure sample: " + e.getMessage());
        }

        if (sampleAttributes == null || sampleAttributes.isEmpty()) {
            return errorResult("No Azure AD sample data found for entity type: " + entityType);
        }

        // 2. Load custom field definitions + English labels
        List<FieldDefinitionsEntity> fields =
                fieldDefinitionsRepository.findByEntityTypeAndIsSystemFalseOrderByDisplayOrder(entityType);

        String translationType = entityType + "_fields";
        Map<String, String> labelMap = new HashMap<>();
        dynamicTranslationsRepository.findAllByLangCodeAndType("en", translationType)
                .ifPresent(list -> list.forEach(t -> labelMap.put(t.getTranslationKey(), t.getTranslatedText())));

        // 3. Build system fields list (built-ins + custom)
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
            String azureAttributesJson = objectMapper.writeValueAsString(sampleAttributes);
            String systemFieldsJson = objectMapper.writeValueAsString(systemFields);
            String prompt = buildPrompt(azureAttributesJson, systemFieldsJson, entityType);

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

    private String buildPrompt(String azureAttributesJson, String systemFieldsJson, String entityType) {
        return "You are a data integration assistant helping map Microsoft Graph API (Azure AD) attributes to application fields.\n\n" +
                "AZURE AD ATTRIBUTES from a sample " + entityType + " record:\n" +
                azureAttributesJson + "\n\n" +
                "APPLICATION FIELDS available for entity type \"" + entityType + "\":\n" +
                systemFieldsJson + "\n\n" +
                "TASK:\n" +
                "1. For each Azure attribute, find the best matching application field.\n" +
                "   - If confident it matches, include it in \"mappings\" with confidence: high/medium/low.\n" +
                "   - If there is no suitable field, include it in \"missingFields\" with a suggested new field definition.\n" +
                "2. Skip Azure technical/internal attributes: id, @odata.type, @odata.context, " +
                "createdDateTime, deletedDateTime, onPremisesLastSyncDateTime, onPremisesSyncEnabled, " +
                "onPremisesImmutableId, onPremisesSecurityIdentifier, proxyAddresses, " +
                "securityEnabled, groupTypes, membershipRule, membershipRuleProcessingState.\n\n" +
                "Return ONLY valid JSON in this exact format:\n" +
                "{\n" +
                "  \"mappings\": [\n" +
                "    {\n" +
                "      \"azureAttribute\": \"userPrincipalName\",\n" +
                "      \"systemFieldKey\": \"username\",\n" +
                "      \"confidence\": \"high\",\n" +
                "      \"reasoning\": \"userPrincipalName is the Azure AD login identifier\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"missingFields\": [\n" +
                "    {\n" +
                "      \"azureAttribute\": \"employeeId\",\n" +
                "      \"suggestedFieldKey\": \"employee_id\",\n" +
                "      \"suggestedLabel\": \"Employee ID\",\n" +
                "      \"suggestedFieldType\": \"text\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    private AzureAiMappingResultDto parseResponse(String rawResponse, Map<String, String> sampleAttributes) {
        try {
            String cleaned = rawResponse
                    .replaceAll("(?s)```[a-zA-Z]*\\n?", "")
                    .replace("```", "")
                    .trim();

            AzureAiMappingResultDto result = objectMapper.readValue(cleaned, AzureAiMappingResultDto.class);

            if (result.getMappings() != null) {
                for (AzureMappingSuggestionDto m : result.getMappings()) {
                    m.setAzureSampleValue(sampleAttributes.getOrDefault(m.getAzureAttribute(), ""));
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

    private AzureAiMappingResultDto errorResult(String message) {
        AzureAiMappingResultDto result = new AzureAiMappingResultDto();
        result.setMappings(new ArrayList<>());
        result.setMissingFields(new ArrayList<>());
        result.setHasUnmappedAttributes(false);
        result.setError(message);
        return result;
    }
}
