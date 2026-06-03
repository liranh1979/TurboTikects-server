package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.BulkTranslateResponseDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiTranslationService {

    private final AiSettingsService aiSettingsService;

    public AiTranslationService(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    /**
     * Translates the provided English key→value map into the target language.
     * The caller is responsible for scoping the map to the correct translation type
     * (system, user_fields, group_fields, ticket_fields, etc.) so that enum option
     * keys (e.g. status_opt_new) and unrelated types are never mixed.
     */
    public BulkTranslateResponseDto bulkTranslate(Map<String, String> englishSource, String targetLanguage)
            throws IOException, URISyntaxException, InterruptedException {

        BulkTranslateResponseDto response = new BulkTranslateResponseDto();

        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            response.setSuccess(false);
            return response;
        }

        if (englishSource == null || englishSource.isEmpty()) {
            response.setSuccess(false);
            return response;
        }

        ObjectMapper mapper = new ObjectMapper();
        String payload = mapper.writeValueAsString(englishSource);

        List<LlmStructure> messages = new ArrayList<>();
        LlmStructure system = new LlmStructure();
        LlmStructure user   = new LlmStructure();
        system.setRole("system");
        system.setContent("You are a professional technical translator. Respond only with a valid JSON object, no markdown, no explanation.");
        user.setRole("user");
        user.setContent("Translate the values in the following JSON from English to " + targetLanguage +
                ". Keep all keys exactly the same. Return only the JSON object.\ndata:\n" + payload);
        messages.add(system);
        messages.add(user);

        String raw     = aiSettingsService.sendLlmRequest(aiSettings, messages);
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, String> translated = mapper.readValue(
                cleaned, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));

        response.setSuccess(true);
        response.setTranslations(translated);
        return response;
    }
}
