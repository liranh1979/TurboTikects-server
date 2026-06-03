package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AddLanguageRequestDto;
import com.turbotikects.turbotikectsserver.dto.AiTranslatioDto;
import com.turbotikects.turbotikectsserver.dto.UpdateTranslationsRequestDto;
import com.turbotikects.turbotikectsserver.entitys.SystemLanguagesEntity;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.DynamicTranslationsService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@RestController
public class DynamicTranslationsController {


    private final DynamicTranslationsService dynamicTranslationsService;

    public DynamicTranslationsController(DynamicTranslationsService dynamicTranslationsService) {
        this.dynamicTranslationsService = dynamicTranslationsService;
    }

    @GetMapping("/api/v1/locales/{lang}")
    public Map<String,String> loginWithCookie(@PathVariable String lang) {
        return dynamicTranslationsService.getSystemFields(lang);
    }

    @PostMapping("/api/v1/languages/add")
    @RequirePermission("MANAGE_LANGUAGES")
    public void addLanguages(@RequestBody AddLanguageRequestDto addLanguageRequestDto ){
        dynamicTranslationsService.addLanguage(addLanguageRequestDto);

    }

    @GetMapping("/api/v1/languages")
    public List<SystemLanguagesEntity> getLanguages() {
        return dynamicTranslationsService.getLanguages();
    }

    @DeleteMapping("/api/v1/languages/{code}")
    @RequirePermission("MANAGE_LANGUAGES")
    public void deleteLanguage(@PathVariable String code) {
        dynamicTranslationsService.deleteLanguage(code);
    }

    @PostMapping("/api/v1/languages/update")
    @RequirePermission("MANAGE_LANGUAGES")
    public void updateTranslations(@RequestBody UpdateTranslationsRequestDto dto) {
        dynamicTranslationsService.updateTranslations(dto);
    }
}
