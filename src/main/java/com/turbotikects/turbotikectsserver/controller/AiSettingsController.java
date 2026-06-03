package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.AiSettingsDto;
import com.turbotikects.turbotikectsserver.dto.BulkTranslateRequestDto;
import com.turbotikects.turbotikectsserver.dto.BulkTranslateResponseDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.AiSettingsService;
import com.turbotikects.turbotikectsserver.services.AiTranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequirePermission("MANAGE_AI")
public class AiSettingsController {

    @Autowired
    AiSettingsService aiSettingsService;

    @Autowired
    AiTranslationService aiTranslationService;

    @GetMapping("/settings")
    public List<AiSettingsDto> getAllAiSettings(){
        return aiSettingsService.getAiSettings();

    }

    @PostMapping("/settings")
    public void addAiSettings(@RequestBody AiSettingsDto aiSettingsDto){
        aiSettingsService.addAiSettings(aiSettingsDto);
    }

    @DeleteMapping("/settings/{id}")
    public void deleteAiSettings(@PathVariable Long aiSettingID){
        aiSettingsService.deleteIASetting(aiSettingID);
    }

    @PatchMapping("/settings/{id}/activate")
    public void activateAiSettings(@PathVariable Long id){
        aiSettingsService.setActive(id);
    }

    @PostMapping("/settings/{id}/test")
    public AiSettingTestResultDto testAiSettings(@PathVariable Long id) throws URISyntaxException, IOException, InterruptedException {
        return aiSettingsService.testIASetting(id);
    }

    @GetMapping("/status")
    public Map<String, Boolean> getAiStatus() throws URISyntaxException, IOException, InterruptedException {
        return aiSettingsService.getAiStatus();
    }

    @PostMapping("/translate-bulk")
    public BulkTranslateResponseDto translateBulk(@RequestBody BulkTranslateRequestDto dto) throws URISyntaxException, IOException, InterruptedException {
        return aiTranslationService.bulkTranslate(dto.getTargetLanguage());
    }

}
