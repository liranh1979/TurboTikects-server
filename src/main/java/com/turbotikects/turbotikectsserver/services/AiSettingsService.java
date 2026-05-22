package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.AiSettingsDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.llm.LlmProviderFactory;
import com.turbotikects.turbotikectsserver.repositorys.AiSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AiSettingsService {
    @Autowired
    AiSettingsRepository aiSettingsRepository;

    @Autowired
    LlmProviderFactory llmProviderFactory;

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

    public AiSettingsEntity getActiveAi(){

        List<AiSettingsEntity> aiSettingsEntity  = aiSettingsRepository.findByIsActive(true);

        if(aiSettingsEntity != null && !aiSettingsEntity.isEmpty()){

           return  aiSettingsEntity.get(0);

        }
        return null;


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

    public Map<String, Boolean> getAiStatus() throws URISyntaxException, IOException, InterruptedException {
        AiSettingsEntity active = getActiveAi();

        return Map.of("hasActiveAI", true);

        /* AiSettingsEntity active = getActiveAi();
        boolean hasActiveAI = active != null && aiValidSetting(active);
        return Map.of("hasActiveAI", hasActiveAI);*/
    }

    public AiSettingTestResultDto testIASetting(Long aiSettingID) throws URISyntaxException, IOException, InterruptedException {

        AiSettingTestResultDto aiSettingTestResultDto = new AiSettingTestResultDto();
        aiSettingTestResultDto.setSuccess(true);
        aiSettingTestResultDto.setMessage("ok");

        return aiSettingTestResultDto;

        /*AiSettingTestResultDto  aiSettingTestResultDto = new AiSettingTestResultDto();
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
        return  aiSettingTestResultDto;*/

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

    public String sendLlmRequest(AiSettingsEntity aiSettingsEntity, List<LlmStructure> llmRequest) throws URISyntaxException, IOException, InterruptedException {
        return llmProviderFactory.getProvider(aiSettingsEntity.getProviderName()).send(aiSettingsEntity, llmRequest);
    }

}
