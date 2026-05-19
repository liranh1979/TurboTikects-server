package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.AiSettingsDto;
import com.turbotikects.turbotikectsserver.services.AiSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@CrossOrigin(origins = "${app.cors.origins}")
@RestController
@RequestMapping("/api/v1/ai")
public class AiSettingsController {

    @Autowired
    AiSettingsService aiSettingsService;

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


}
