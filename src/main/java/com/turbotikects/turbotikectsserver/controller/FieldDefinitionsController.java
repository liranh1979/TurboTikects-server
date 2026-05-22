package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.FieldDefinitionDto;
import com.turbotikects.turbotikectsserver.dto.UpdateTranslationsRequestDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.services.FieldDefinitionsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "${app.cors.origins}")
@RestController
@RequestMapping("/api/v1/field-definitions")
public class FieldDefinitionsController {

    private final FieldDefinitionsService fieldDefinitionsService;

    public FieldDefinitionsController(FieldDefinitionsService fieldDefinitionsService) {
        this.fieldDefinitionsService = fieldDefinitionsService;
    }

    @GetMapping
    public List<FieldDefinitionsEntity> getCustomFields() {
        return fieldDefinitionsService.getCustomFields();
    }

    @PostMapping
    public void addFieldDefinition(@RequestBody FieldDefinitionDto dto) {
        fieldDefinitionsService.addFieldDefinition(dto);
    }

    @GetMapping("/translations/{lang}")
    public Map<String, String> getFieldTranslations(@PathVariable String lang) {
        return fieldDefinitionsService.getFieldTranslations(lang);
    }

    @PostMapping("/translations/update")
    public void updateFieldTranslations(@RequestBody UpdateTranslationsRequestDto dto) {
        fieldDefinitionsService.updateFieldTranslations(dto);
    }

    @PatchMapping("/{id}/list-visibility")
    public void setListVisibility(@PathVariable Long id, @RequestParam boolean visible) {
        fieldDefinitionsService.setListVisibility(id, visible);
    }
}