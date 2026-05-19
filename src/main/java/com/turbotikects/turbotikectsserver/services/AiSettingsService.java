package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.AiSettingsDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmResponse;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.repositorys.AiSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AiSettingsService {
    @Autowired
    AiSettingsRepository aiSettingsRepository;

    public List<AiSettingsDto> getAiSettings() {

        List<AiSettingsDto> aiSettings = new ArrayList<>();

       List<AiSettingsEntity> aiSettingsEntityList = aiSettingsRepository.findAll();
        for (AiSettingsEntity aiSettingsEntity : aiSettingsEntityList) {
            AiSettingsDto aiSettingsDto = new AiSettingsDto();
            aiSettingsDto.setId(aiSettingsEntity.getId());
            aiSettingsDto.setModelName(aiSettingsEntity.getModelName());
            aiSettingsDto.setBaseUrl(aiSettingsEntity.getBaseUrl());
            aiSettingsDto.setActive(aiSettingsEntity.isActive());
            aiSettingsDto.setProviderName(aiSettingsEntity.getProviderName());
            aiSettings.add(aiSettingsDto);
        }

        return aiSettings;
    }

    public void addAiSettings(AiSettingsDto aiSettingsDto){

        AiSettingsEntity aiSettingsEntity = new AiSettingsEntity();
        aiSettingsEntity.setModelName(aiSettingsDto.getModelName());
        aiSettingsEntity.setBaseUrl(aiSettingsDto.getBaseUrl());
        aiSettingsEntity.setActive(false);
        aiSettingsEntity.setApiKey(aiSettingsDto.getApiKey());
        aiSettingsEntity.setCreatedAt(LocalDateTime.now());
        aiSettingsEntity.setUpdatedAt(LocalDateTime.now());
        aiSettingsEntity.setProviderName(aiSettingsDto.getProviderName());
        aiSettingsRepository.save(aiSettingsEntity);

    }

    public void deleteIASetting(Long aiSettingID){

        AiSettingsEntity aiSettingsEntity = new AiSettingsEntity();
        aiSettingsEntity.setId(aiSettingID);

        aiSettingsRepository.delete(aiSettingsEntity);
        aiSettingsRepository.flush();
    }

    public void setActive(Long aiSettingID){
        AiSettingsEntity aiSettingsEntity = new AiSettingsEntity();
        aiSettingsEntity.setId(aiSettingID);

        aiSettingsRepository.deactivateAllSettings();
        aiSettingsRepository.deactivatedSettings(aiSettingID);
        aiSettingsRepository.flush();
    }

    public AiSettingTestResultDto testIASetting(Long aiSettingID) throws URISyntaxException, IOException, InterruptedException {

        AiSettingTestResultDto  aiSettingTestResultDto = new AiSettingTestResultDto();
        Optional<AiSettingsEntity> aiSettingsEntity  = aiSettingsRepository.findById(aiSettingID);

        if(aiSettingsEntity.isEmpty()){
            aiSettingTestResultDto.setSuccess(false);
            aiSettingTestResultDto.setMessage("can not found setting for id " + aiSettingID);
            return aiSettingTestResultDto;
        }

        if(aiValidSetting(aiSettingsEntity.get())){
            aiSettingTestResultDto.setSuccess(true);
            aiSettingTestResultDto.setMessage("ok");
            return aiSettingTestResultDto;
        }


        aiSettingTestResultDto.setSuccess(false);
        aiSettingTestResultDto.setMessage("AI not working");
        return  aiSettingTestResultDto;

    }

    private boolean  aiValidSetting(AiSettingsEntity aiSettingsEntity) throws URISyntaxException, IOException, InterruptedException {

        List<LlmStructure> llmRequest = new ArrayList<>();

        LlmStructure system = new LlmStructure();
        LlmStructure user = new LlmStructure();
        system.setRole("system");
        system.setContent("You are a validator. Respond only with JSON.");
        user.setRole("user");
        user.setContent("Return exactly: {\"status\": \"1\"}");

        llmRequest.add(system);
        llmRequest.add(user);


       String  validSettingResponse =  sendLlmRequest(aiSettingsEntity,llmRequest);
        ObjectMapper mapper = new ObjectMapper();

        Map responseBody = mapper.readValue(validSettingResponse,Map.class);

        if(responseBody != null){
            return responseBody.get("status") != null && "1".equals(responseBody.get("status"));
        }
        return false;

    }

    private String sendLlmRequest(AiSettingsEntity aiSettingsEntity,List<LlmStructure> llmRequest) throws URISyntaxException, IOException, InterruptedException {

        ObjectMapper mapper = new ObjectMapper();
        double temperature = 0.3;

        String url  = aiSettingsEntity.getBaseUrl().trim();
        if(url.charAt(url.length() -1) != '/'){
            url = url + "/";
        }

        HashMap<String,Object >payload = new HashMap<>();
        payload.put("model",aiSettingsEntity.getModelName());
        payload.put("messages",llmRequest);
        payload.put("temperature", Double.toString(temperature));

        String payloadString = mapper.writeValueAsString(payload);

        url = url + "chat/completions";

        HttpClient httpClient =  HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder(new URI(url)).header("Content-Type", "application/json").header("Authorization", "Bearer " + aiSettingsEntity.getApiKey() ).POST(HttpRequest.BodyPublishers.ofString(payloadString)).build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        log.info("LLM requst: {} \nLLM response {}", llmRequest,response);
        return extractContentFromResponse(response);

    }

    private String extractContentFromResponse(HttpResponse<String> response) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Convert the raw JSON string into our Java Object
        LlmResponse llmResponse = mapper.readValue(response.body(), LlmResponse.class);

        // 2. Extract the content (usually from the first choice)
        if (llmResponse.getChoices() != null && !llmResponse.getChoices().isEmpty()) {
            return llmResponse.getChoices().get(0).getMessage().getContent();
        }

        return "No content found";
    }

}
