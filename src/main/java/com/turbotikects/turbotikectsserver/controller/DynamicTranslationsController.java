package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.DynamicTranslationsSystemFieldsDto;
import com.turbotikects.turbotikectsserver.services.DynamicTranslationsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "${app.cors.origins}")
@RestController
public class DynamicTranslationsController {

    @Autowired
    private DynamicTranslationsService dynamicTranslationsService;

    @GetMapping("/api/v1/locales/{lang}")
    public Map<String,String> loginWithCookie(@PathVariable String lang) {
        return dynamicTranslationsService.getSystemFields(lang);
    }

    @PostMapping("/api/v1/locales/AiTranslation")
    public Map<String,String> aiTranslation(){
        return null;
    }
}
