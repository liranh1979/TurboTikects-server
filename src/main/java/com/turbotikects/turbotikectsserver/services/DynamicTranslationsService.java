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
        String type = dto.getType() != null ? dto.getType() : "system";

        List<DynamicTranslationsEntity> allForLang = dynamicTranslationsRepository.findByLangCode(dto.getLang());

        // Index only rows belonging to this type — (lang_code, translation_key, type) is the
        // real unique key, so matching on translationKey alone could update the wrong row if
        // the same key string also exists under a different type for this language.
        Map<String, DynamicTranslationsEntity> byKey = new HashMap<>();
        for (DynamicTranslationsEntity e : allForLang) {
            if (type.equals(e.getType())) {
                byKey.put(e.getTranslationKey(), e);
            }
        }

        List<DynamicTranslationsEntity> toSave = new ArrayList<>();
        for (Map.Entry<String, String> entry : dto.getTranslations().entrySet()) {
            String key = entry.getKey();
            DynamicTranslationsEntity existing = byKey.get(key);
            if (existing != null) {
                existing.setTranslatedText(entry.getValue());
                existing.setUpdateData(LocalDateTime.now());
                toSave.add(existing);
            } else {
                // Missing row — happens whenever a key was added to English only (e.g. via a
                // later Flyway migration) after this language was created. Without this branch
                // an AI-translated value shows up in the UI but is silently dropped on save,
                // reappearing blank after the next reload.
                DynamicTranslationsEntity newEntity = new DynamicTranslationsEntity();
                newEntity.setLangCode(dto.getLang());
                newEntity.setTranslationKey(key);
                newEntity.setTranslatedText(entry.getValue());
                newEntity.setType(type);
                newEntity.setUpdateData(LocalDateTime.now());
                toSave.add(newEntity);
            }
        }

        dynamicTranslationsRepository.saveAll(toSave);
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
