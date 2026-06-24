package com.turbotikects.turbotikectsserver.services;

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

    public AiSettingTestResultDto testIASetting(Long aiSettingID) throws Exception {
        AiSettingsEntity entity = aiSettingsRepository.findById(aiSettingID).orElseThrow();
        return llmProviderFactory.getProvider(entity.getProviderName()).validateKey(entity);
    }

    public String sendLlmRequest(AiSettingsEntity aiSettingsEntity, List<LlmStructure> llmRequest) throws URISyntaxException, IOException, InterruptedException {
        return llmProviderFactory.getProvider(aiSettingsEntity.getProviderName()).send(aiSettingsEntity, llmRequest);
    }

    public List<String> listModels(String providerName, String apiKey) throws URISyntaxException, IOException, InterruptedException {
        return llmProviderFactory.getProvider(providerName).listModels(apiKey);
    }

}
