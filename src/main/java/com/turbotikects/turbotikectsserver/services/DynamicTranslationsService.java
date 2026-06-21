package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.AddLanguageRequestDto;
import com.turbotikects.turbotikectsserver.dto.AiTranslatioDto;
import com.turbotikects.turbotikectsserver.dto.UpdateTranslationsRequestDto;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.entitys.SystemLanguagesEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.SystemLanguagesRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DynamicTranslationsService {

    private final DynamicTranslationsRepository dynamicTranslationsRepository;
    private final AiTranslationService aiTranslationService;
    private final SystemLanguagesRepository systemLanguagesRepository;

    public DynamicTranslationsService(DynamicTranslationsRepository dynamicTranslationsRepository, AiTranslationService aiTranslationService, SystemLanguagesRepository systemLanguagesRepository) {
        this.dynamicTranslationsRepository = dynamicTranslationsRepository;
        this.aiTranslationService = aiTranslationService;
        this.systemLanguagesRepository = systemLanguagesRepository;
    }


    @Cacheable(cacheNames = "system_fields", key = "#langCode")
    public Map<String,String> getSystemFields(String langCode){

        Map<String,String> systemFields = new HashMap<>();

        // English is the master key set: any key added via migration only ever gets an
        // English row (per project convention), so non-English languages fall back to it
        // for keys they don't have yet — otherwise those keys vanish from lists entirely
        // and untranslated strings render blank instead of showing English.
        dynamicTranslationsRepository.findAllByLangCodeAndType("en", "system")
                .ifPresent(list -> list.forEach(e -> systemFields.put(e.getTranslationKey(), e.getTranslatedText())));

        if (!"en".equals(langCode)) {
            dynamicTranslationsRepository.findAllByLangCodeAndType(langCode, "system")
                    .ifPresent(list -> list.forEach(e -> {
                        if (e.getTranslatedText() != null && !e.getTranslatedText().isBlank()) {
                            systemFields.put(e.getTranslationKey(), e.getTranslatedText());
                        }
                    }));
        }

        return systemFields;
    }

    public void addLanguage(AddLanguageRequestDto addLanguageRequestDto){

        SystemLanguagesEntity systemLanguagesEntity = new SystemLanguagesEntity();

        systemLanguagesEntity.setCode(addLanguageRequestDto.getCode());
        systemLanguagesEntity.setName(addLanguageRequestDto.getName());
        systemLanguagesEntity.setCreatedAt(LocalDateTime.now());
        systemLanguagesRepository.save(systemLanguagesEntity);
        systemLanguagesRepository.flush();

        copyAllFilesToNewLanguage(addLanguageRequestDto);
    }


    public List<SystemLanguagesEntity> getLanguages() {
        return systemLanguagesRepository.findAll();
    }

    @Transactional
    @CacheEvict(cacheNames = "system_fields", allEntries = true)
    public void deleteLanguage(String code) {
        dynamicTranslationsRepository.deleteByLangCode(code);
        systemLanguagesRepository.deleteById(code);
    }

    // allEntries: every cached language now embeds English as a fallback for its missing
    // keys, so editing English (or any language) can change what other languages resolve to —
    // a single-key evict would leave their cached fallback values stale.
    @Transactional
    @CacheEvict(cacheNames = "system_fields", allEntries = true)
    public void updateTranslations(UpdateTranslationsRequestDto dto) {
        List<DynamicTranslationsEntity> entities = dynamicTranslationsRepository.findByLangCode(dto.getLang());
        Map<String, DynamicTranslationsEntity> byKey = new HashMap<>();
        for (DynamicTranslationsEntity e : entities) {
            byKey.put(e.getTranslationKey(), e);
        }
        for (Map.Entry<String, String> entry : dto.getTranslations().entrySet()) {
            DynamicTranslationsEntity entity = byKey.get(entry.getKey());
            if (entity != null) {
                entity.setTranslatedText(entry.getValue());
                entity.setUpdateData(LocalDateTime.now());
            }
        }
        dynamicTranslationsRepository.saveAll(byKey.values());
    }

    private void copyAllFilesToNewLanguage(AddLanguageRequestDto addLanguageRequestDto){

        List<DynamicTranslationsEntity> files =  dynamicTranslationsRepository.findByLangCode("en");
        List<DynamicTranslationsEntity> newfiles =  new ArrayList<>();

        for(DynamicTranslationsEntity dynamicTranslationsEntity: files){
            DynamicTranslationsEntity newfiled = new DynamicTranslationsEntity();
            newfiled.setLangCode(addLanguageRequestDto.getCode());
            newfiled.setTranslationKey(dynamicTranslationsEntity.getTranslationKey());
            newfiled.setType(dynamicTranslationsEntity.getType());
            newfiled.setTranslatedText("");
            newfiles.add(newfiled);

        }

        dynamicTranslationsRepository.saveAll(newfiles);
        dynamicTranslationsRepository.flush();

    }

}
