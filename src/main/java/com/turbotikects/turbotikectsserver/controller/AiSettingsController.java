package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.AiSettingsDto;
import com.turbotikects.turbotikectsserver.dto.BulkTranslateRequestDto;
import com.turbotikects.turbotikectsserver.dto.BulkTranslateResponseDto;
import com.turbotikects.turbotikectsserver.dto.ListModelsRequestDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.llm.AnthropicLlmProvider;
import com.turbotikects.turbotikectsserver.llm.DeepSeekLlmProvider;
import com.turbotikects.turbotikectsserver.llm.GeminiLlmProvider;
import com.turbotikects.turbotikectsserver.llm.GeminiStableLlmProvider;
import com.turbotikects.turbotikectsserver.llm.OpenAiLlmProvider;
import com.turbotikects.turbotikectsserver.llm.OpenRouterLlmProvider;
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

    @GetMapping("/providers")
    public List<LlmProviderInfoDto> getProviders() {
        return List.of(OpenAiLlmProvider.INFO, AnthropicLlmProvider.INFO, GeminiLlmProvider.INFO, GeminiStableLlmProvider.INFO, DeepSeekLlmProvider.INFO, OpenRouterLlmProvider.INFO);
    }

    @PostMapping("/models")
    public List<String> listModels(@RequestBody ListModelsRequestDto dto) throws IOException, URISyntaxException, InterruptedException {
        return aiSettingsService.listModels(dto.getProviderName(), dto.getApiKey());
    }

    @GetMapping("/settings")
    public List<AiSettingsDto> getAllAiSettings(){
        return aiSettingsService.getAiSettings();
    }

    @PostMapping("/settings")
    public void addAiSettings(@RequestBody AiSettingsDto aiSettingsDto){
        aiSettingsService.addAiSettings(aiSettingsDto);
    }

    @DeleteMapping("/settings/{id}")
    public void deleteAiSettings(@PathVariable("id") Long aiSettingID){
        aiSettingsService.deleteIASetting(aiSettingID);
    }

    @PatchMapping("/settings/{id}/activate")
    public void activateAiSettings(@PathVariable Long id){
        aiSettingsService.setActive(id);
    }

    @PostMapping("/settings/{id}/test")
    public AiSettingTestResultDto testAiSettings(@PathVariable Long id) throws Exception {
        return aiSettingsService.testIASetting(id);
    }

    @GetMapping("/status")
    public Map<String, Boolean> getAiStatus() throws URISyntaxException, IOException, InterruptedException {
        return aiSettingsService.getAiStatus();
    }

    @PostMapping("/translate-bulk")
    public BulkTranslateResponseDto translateBulk(@RequestBody BulkTranslateRequestDto dto) throws URISyntaxException, IOException, InterruptedException {
        return aiTranslationService.bulkTranslate(dto.getTranslations(), dto.getTargetLanguage());
    }

}
