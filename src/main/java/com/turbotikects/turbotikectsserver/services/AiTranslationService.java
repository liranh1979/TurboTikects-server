package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiTranslatioDto;
import com.turbotikects.turbotikectsserver.dto.BulkTranslateResponseDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import io.micrometer.common.util.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiTranslationService {

    private final AiSettingsService aiSettingsService;

    private final DynamicTranslationsRepository dynamicTranslationsRepository;

    public AiTranslationService(AiSettingsService aiSettingsService, DynamicTranslationsRepository dynamicTranslationsRepository) {
        this.aiSettingsService = aiSettingsService;
        this.dynamicTranslationsRepository = dynamicTranslationsRepository;
    }

    public BulkTranslateResponseDto bulkTranslate(Map<String, String> translations, String targetLanguage) throws IOException, URISyntaxException, InterruptedException {
        BulkTranslateResponseDto response = new BulkTranslateResponseDto();

        AiSettingsEntity aiSettingsEntity = aiSettingsService.getActiveAi();
        if (aiSettingsEntity == null) {
            response.setSuccess(false);
            return response;
        }

        HashMap<String,String> englishTranslationsMap = convertListIntoMap( dynamicTranslationsRepository.findByLangCode("en"));
        HashMap<String,String> targetTranslationsMap = convertListIntoMap( dynamicTranslationsRepository.findByLangCode(targetLanguage));

        Map<String, String> translationsToSend = new HashMap<>();
        for(String key: targetTranslationsMap.keySet()){
            if(StringUtils.isBlank(targetTranslationsMap.get(key))){
                translationsToSend.put(key,englishTranslationsMap.get(key));
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String english = objectMapper.writeValueAsString(translationsToSend);

        List<LlmStructure> llmRequest = new ArrayList<>();
        LlmStructure system = new LlmStructure();
        LlmStructure user = new LlmStructure();
        system.setRole("system");
        system.setContent("You are a professional technical translator. Respond only with a valid JSON object, no markdown, no explanation.");
        user.setRole("user");
        user.setContent("Translate the values in the following JSON from English to " + targetLanguage + ". Keep all keys exactly the same. Return only the JSON object.\ndata:\n" + english);
        llmRequest.add(system);
        llmRequest.add(user);

        String result = aiSettingsService.sendLlmRequest(aiSettingsEntity, llmRequest);
        // Strip markdown code fences if the model wrapped the response
        String cleaned = result.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, String> translated = objectMapper.readValue(cleaned, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));

        for(String key: translated.keySet()){
            translations.put(key, translated.get(key));
        }

        response.setSuccess(true);
        response.setTranslations(translations);
        return response;
    }

    private HashMap<String,String> convertListIntoMap(List<DynamicTranslationsEntity>  englishTranslationsRepository){

        HashMap<String,String> englishTranslationsMap = new HashMap<>();
        for(DynamicTranslationsEntity translationsEntity:  englishTranslationsRepository){

            englishTranslationsMap.put(translationsEntity.getTranslationKey(),translationsEntity.getTranslatedText());

        }
        return  englishTranslationsMap;


    }

}
