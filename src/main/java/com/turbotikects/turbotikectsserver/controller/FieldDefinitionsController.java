package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.FieldDefinitionDto;
import com.turbotikects.turbotikectsserver.dto.UpdateTranslationsRequestDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.FieldDefinitionsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/field-definitions")
@RequirePermission("MANAGE_FIELDS")
public class FieldDefinitionsController {

    private final FieldDefinitionsService fieldDefinitionsService;

    public FieldDefinitionsController(FieldDefinitionsService fieldDefinitionsService) {
        this.fieldDefinitionsService = fieldDefinitionsService;
    }

    @GetMapping
    public List<FieldDefinitionsEntity> getCustomFields(
            @RequestParam(defaultValue = "user") String entityType) {
        return fieldDefinitionsService.getCustomFields(entityType);
    }

    @PostMapping
    public void addFieldDefinition(@RequestBody FieldDefinitionDto dto) {
        fieldDefinitionsService.addFieldDefinition(dto);
    }

    @GetMapping("/translations/{lang}")
    public Map<String, String> getFieldTranslations(
            @PathVariable String lang,
            @RequestParam(defaultValue = "user_fields") String translationType) {
        return fieldDefinitionsService.getFieldTranslations(lang, translationType);
    }

    @PostMapping("/translations/update")
    public void updateFieldTranslations(@RequestBody UpdateTranslationsRequestDto dto) {
        fieldDefinitionsService.updateFieldTranslations(dto);
    }

    @PatchMapping("/{id}/list-visibility")
    public void setListVisibility(@PathVariable Long id, @RequestParam boolean visible) {
        fieldDefinitionsService.setListVisibility(id, visible);
    }

    @PatchMapping("/{id}/options")
    public void updateFieldOptions(@PathVariable Long id, @RequestBody List<String> options) {
        fieldDefinitionsService.updateFieldOptions(id, options);
    }
}