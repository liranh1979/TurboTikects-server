package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.AlertTypeDto;
import com.turbotikects.turbotikectsserver.dto.SaveAlertTypeRequestDto;
import com.turbotikects.turbotikectsserver.entitys.AlertTypeEntity;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.entitys.SystemLanguagesEntity;
import com.turbotikects.turbotikectsserver.repositorys.AlertTypeRepository;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.SystemLanguagesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlertTypeService {

    private final AlertTypeRepository alertTypeRepo;
    private final DynamicTranslationsRepository dynamicTranslationsRepo;
    private final SystemLanguagesRepository systemLanguagesRepo;

    private static final String TRANSLATION_TYPE = "alert_types";

    public AlertTypeService(AlertTypeRepository alertTypeRepo,
                             DynamicTranslationsRepository dynamicTranslationsRepo,
                             SystemLanguagesRepository systemLanguagesRepo) {
        this.alertTypeRepo = alertTypeRepo;
        this.dynamicTranslationsRepo = dynamicTranslationsRepo;
        this.systemLanguagesRepo = systemLanguagesRepo;
    }

    public List<AlertTypeDto> getAll() {
        return alertTypeRepo.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AlertTypeDto create(SaveAlertTypeRequestDto dto) {
        String typeKey = dto.getTypeKey();
        if (typeKey == null || typeKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "typeKey is required");
        }
        if (alertTypeRepo.existsByTypeKey(typeKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An alert type with that key already exists");
        }

        AlertTypeEntity entity = new AlertTypeEntity();
        entity.setTypeKey(typeKey);
        entity.setColor(dto.getColor() != null ? dto.getColor() : "info");
        entity.setIcon(dto.getIcon());
        entity.setIsSystem(false);
        entity.setDisplayOrder(alertTypeRepo.findAllByOrderByDisplayOrderAsc().size() + 1);
        entity = alertTypeRepo.save(entity);

        List<SystemLanguagesEntity> languages = systemLanguagesRepo.findAll();
        List<DynamicTranslationsEntity> translations = new ArrayList<>();
        for (SystemLanguagesEntity lang : languages) {
            DynamicTranslationsEntity translation = new DynamicTranslationsEntity();
            translation.setLangCode(lang.getCode());
            translation.setTranslationKey(typeKey);
            translation.setType(TRANSLATION_TYPE);
            translation.setTranslatedText("en".equals(lang.getCode()) ? dto.getName() : "");
            translation.setUpdateData(LocalDateTime.now());
            translations.add(translation);
        }
        dynamicTranslationsRepo.saveAll(translations);

        return toDto(entity);
    }

    public AlertTypeDto update(Long id, SaveAlertTypeRequestDto dto) {
        AlertTypeEntity entity = alertTypeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert type not found"));
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity = alertTypeRepo.save(entity);
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        AlertTypeEntity entity = alertTypeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert type not found"));
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete a built-in alert type");
        }
        dynamicTranslationsRepo.deleteByTypeAndTranslationKey(TRANSLATION_TYPE, entity.getTypeKey());
        alertTypeRepo.deleteById(id);
    }

    private AlertTypeDto toDto(AlertTypeEntity e) {
        AlertTypeDto dto = new AlertTypeDto();
        dto.setId(e.getId());
        dto.setTypeKey(e.getTypeKey());
        dto.setColor(e.getColor());
        dto.setIcon(e.getIcon());
        dto.setIsSystem(e.getIsSystem());
        dto.setDisplayOrder(e.getDisplayOrder());
        return dto;
    }
}
