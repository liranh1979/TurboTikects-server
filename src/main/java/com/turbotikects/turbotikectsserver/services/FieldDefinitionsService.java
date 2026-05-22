package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.FieldDefinitionDto;
import com.turbotikects.turbotikectsserver.dto.UpdateTranslationsRequestDto;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.SystemLanguagesEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.SystemLanguagesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FieldDefinitionsService {

    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final DynamicTranslationsRepository dynamicTranslationsRepository;
    private final SystemLanguagesRepository systemLanguagesRepository;

    public FieldDefinitionsService(FieldDefinitionsRepository fieldDefinitionsRepository,
                                   DynamicTranslationsRepository dynamicTranslationsRepository,
                                   SystemLanguagesRepository systemLanguagesRepository) {
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.dynamicTranslationsRepository = dynamicTranslationsRepository;
        this.systemLanguagesRepository = systemLanguagesRepository;
    }

    public List<FieldDefinitionsEntity> getCustomFields() {
        return fieldDefinitionsRepository.findByIsSystemFalseOrderByDisplayOrder();
    }

    public void setListVisibility(Long id, boolean visible) {
        fieldDefinitionsRepository.findById(id).ifPresent(f -> {
            f.setListVisible(visible);
            fieldDefinitionsRepository.save(f);
        });
    }

    public Map<String, String> getFieldTranslations(String langCode) {
        Map<String, String> result = new HashMap<>();
        Optional<List<DynamicTranslationsEntity>> entries =
                dynamicTranslationsRepository.findAllByLangCodeAndType(langCode, "user_fields");
        entries.ifPresent(list -> list.forEach(e -> result.put(e.getTranslationKey(), e.getTranslatedText())));
        return result;
    }

    @Transactional
    public void addFieldDefinition(FieldDefinitionDto dto) {
        FieldDefinitionsEntity field = new FieldDefinitionsEntity();
        field.setEntityType(dto.getEntityType() != null ? dto.getEntityType() : "user");
        field.setFieldKey(dto.getFieldKey());
        field.setFieldType(dto.getFieldType());
        field.setListVisible(false);
        field.setDetailVisible(true);
        field.setSystem(false);
        field.setCreatedAt(LocalDateTime.now());
        fieldDefinitionsRepository.save(field);

        List<SystemLanguagesEntity> languages = systemLanguagesRepository.findAll();
        List<DynamicTranslationsEntity> translations = new ArrayList<>();
        for (SystemLanguagesEntity lang : languages) {
            DynamicTranslationsEntity translation = new DynamicTranslationsEntity();
            translation.setLangCode(lang.getCode());
            translation.setTranslationKey(dto.getFieldKey());
            translation.setType("user_fields");
            translation.setTranslatedText("en".equals(lang.getCode()) ? dto.getLabel() : "");
            translation.setUpdateData(LocalDateTime.now());
            translations.add(translation);
        }
        dynamicTranslationsRepository.saveAll(translations);
    }

    @Transactional
    public void updateFieldTranslations(UpdateTranslationsRequestDto dto) {
        List<DynamicTranslationsEntity> allForLang = dynamicTranslationsRepository.findByLangCode(dto.getLang());

        // All existing keys regardless of type — used to avoid duplicate-key violations
        Set<String> allExistingKeys = new HashSet<>();
        Map<String, DynamicTranslationsEntity> userFieldsByKey = new HashMap<>();
        for (DynamicTranslationsEntity e : allForLang) {
            allExistingKeys.add(e.getTranslationKey());
            if ("user_fields".equals(e.getType())) {
                userFieldsByKey.put(e.getTranslationKey(), e);
            }
        }

        List<DynamicTranslationsEntity> toSave = new ArrayList<>();
        for (Map.Entry<String, String> entry : dto.getTranslations().entrySet()) {
            String key = entry.getKey();
            DynamicTranslationsEntity existing = userFieldsByKey.get(key);
            if (existing != null) {
                existing.setTranslatedText(entry.getValue());
                existing.setUpdateData(LocalDateTime.now());
                toSave.add(existing);
            } else if (!allExistingKeys.contains(key)) {
                // Truly new key (pre-seeded field with no translation row yet) — safe to insert
                DynamicTranslationsEntity newEntity = new DynamicTranslationsEntity();
                newEntity.setLangCode(dto.getLang());
                newEntity.setTranslationKey(key);
                newEntity.setTranslatedText(entry.getValue());
                newEntity.setType("user_fields");
                newEntity.setUpdateData(LocalDateTime.now());
                toSave.add(newEntity);
            }
            // key exists with a different type (e.g. 'system') — skip to avoid duplicate-key error
        }

        dynamicTranslationsRepository.saveAll(toSave);
    }
}