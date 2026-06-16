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

    public List<FieldDefinitionsEntity> getCustomFields(String entityType) {
        return fieldDefinitionsRepository.findByEntityTypeOrderByDisplayOrder(entityType);
    }

    public void setListVisibility(Long id, boolean visible) {
        fieldDefinitionsRepository.findById(id).ifPresent(f -> {
            f.setListVisible(visible);
            fieldDefinitionsRepository.save(f);
        });
    }

    public void setAdminOnly(Long id, boolean adminOnly) {
        fieldDefinitionsRepository.findById(id).ifPresent(f -> {
            f.setAdminOnly(adminOnly);
            fieldDefinitionsRepository.save(f);
        });
    }

    public Map<String, String> getFieldTranslations(String langCode, String translationType) {
        Map<String, String> result = new HashMap<>();
        Optional<List<DynamicTranslationsEntity>> entries =
                dynamicTranslationsRepository.findAllByLangCodeAndType(langCode, translationType);
        entries.ifPresent(list -> list.forEach(e -> result.put(e.getTranslationKey(), e.getTranslatedText())));
        return result;
    }

    @Transactional
    public void addFieldDefinition(FieldDefinitionDto dto) {
        FieldDefinitionsEntity field = new FieldDefinitionsEntity();
        String entityType = dto.getEntityType() != null ? dto.getEntityType() : "user";
        String translationType = entityType + "_fields";

        field.setEntityType(entityType);
        field.setFieldKey(dto.getFieldKey());
        field.setFieldType(dto.getFieldType());
        field.setFieldOptions(dto.getFieldOptions());
        if (dto.getFieldConfig() != null) field.setFieldConfig(dto.getFieldConfig());
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
            translation.setType(translationType);
            translation.setTranslatedText("en".equals(lang.getCode()) ? dto.getLabel() : "");
            translation.setUpdateData(LocalDateTime.now());
            translations.add(translation);
        }
        dynamicTranslationsRepository.saveAll(translations);
    }

    public void updateFieldOptions(Long id, List<String> options) {
        fieldDefinitionsRepository.findById(id).ifPresent(f -> {
            f.setFieldOptions(options);
            fieldDefinitionsRepository.save(f);
        });
    }

    public void updateFieldConfig(Long id, Map<String, Object> config) {
        fieldDefinitionsRepository.findById(id).ifPresent(f -> {
            f.setFieldConfig(config);
            fieldDefinitionsRepository.save(f);
        });
    }

    @Transactional
    public void updateFieldTranslations(UpdateTranslationsRequestDto dto) {
        String translationType = dto.getType() != null ? dto.getType() : "user_fields";

        List<DynamicTranslationsEntity> allForLang = dynamicTranslationsRepository.findByLangCode(dto.getLang());

        // Index only rows belonging to this translation type
        Map<String, DynamicTranslationsEntity> fieldsByKey = new HashMap<>();
        for (DynamicTranslationsEntity e : allForLang) {
            if (translationType.equals(e.getType())) {
                fieldsByKey.put(e.getTranslationKey(), e);
            }
        }

        List<DynamicTranslationsEntity> toSave = new ArrayList<>();
        for (Map.Entry<String, String> entry : dto.getTranslations().entrySet()) {
            String key = entry.getKey();
            DynamicTranslationsEntity existing = fieldsByKey.get(key);
            if (existing != null) {
                existing.setTranslatedText(entry.getValue());
                existing.setUpdateData(LocalDateTime.now());
                toSave.add(existing);
            } else {
                DynamicTranslationsEntity newEntity = new DynamicTranslationsEntity();
                newEntity.setLangCode(dto.getLang());
                newEntity.setTranslationKey(key);
                newEntity.setTranslatedText(entry.getValue());
                newEntity.setType(translationType);
                newEntity.setUpdateData(LocalDateTime.now());
                toSave.add(newEntity);
            }
        }

        dynamicTranslationsRepository.saveAll(toSave);
    }
}