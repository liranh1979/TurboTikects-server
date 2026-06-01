package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.AzureMappingService;
import com.turbotikects.turbotikectsserver.services.AzureService;
import com.turbotikects.turbotikectsserver.services.AzureSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "${app.cors.origins}")
@RestController
@RequestMapping("/api/v1/azure")
@RequirePermission("MANAGE_AZURE")
public class AzureController {

    private final AzureService azureService;
    private final AzureMappingService azureMappingService;
    private final AzureSyncService azureSyncService;

    public AzureController(AzureService azureService,
                           AzureMappingService azureMappingService,
                           AzureSyncService azureSyncService) {
        this.azureService = azureService;
        this.azureMappingService = azureMappingService;
        this.azureSyncService = azureSyncService;
    }

    @GetMapping("/configs")
    public List<AzureConfigDto> getAllConfigs() {
        return azureService.getAllConfigs();
    }

    @PostMapping("/configs")
    public AzureConfigDto createConfig(@RequestBody AzureConfigDto dto) {
        return azureService.createConfig(dto);
    }

    @PatchMapping("/configs/{id}")
    public AzureConfigDto updateConfig(@PathVariable Long id, @RequestBody AzureConfigDto dto) {
        return azureService.updateConfig(id, dto);
    }

    @DeleteMapping("/configs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable Long id) {
        azureService.deleteConfig(id);
    }

    @PostMapping("/configs/test")
    public AzureTestResultDto testConnectionRaw(@RequestBody AzureConfigDto dto) {
        return azureService.testConnectionRaw(dto);
    }

    @PostMapping("/configs/{id}/test")
    public AzureTestResultDto testConnection(@PathVariable Long id) {
        return azureService.testConnection(id);
    }

    @PostMapping("/configs/{id}/activate")
    public AzureConfigDto activateConfig(@PathVariable Long id,
                                         @RequestParam(defaultValue = "true") boolean active) {
        return azureService.setActive(id, active);
    }

    @GetMapping("/configs/{id}/sample-user")
    public AzureSampleDto sampleUser(@PathVariable Long id) {
        return azureService.fetchSampleUser(id);
    }

    @GetMapping("/configs/{id}/sample-group")
    public AzureSampleDto sampleGroup(@PathVariable Long id) {
        return azureService.fetchSampleGroup(id);
    }

    @PostMapping("/configs/{id}/suggest-mapping")
    public AzureAiMappingResultDto suggestMapping(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "user") String entityType) {
        return azureMappingService.suggestMappings(id, entityType);
    }

    @GetMapping("/configs/{id}/mappings")
    public Object getMappings(@PathVariable Long id,
                              @RequestParam(required = false) String entityType) {
        if (entityType != null) {
            return azureService.getMappings(id, entityType);
        }
        return azureService.getAllMappings(id);
    }

    @PostMapping("/configs/{id}/mappings")
    public List<AzureFieldMappingDto> saveMappings(@PathVariable Long id,
                                                    @RequestBody SaveAzureMappingsRequestDto dto) {
        return azureService.saveMappings(id, dto);
    }

    @PostMapping("/configs/{id}/sync")
    public Map<String, String> triggerSync(@PathVariable Long id) {
        String taskId = azureSyncService.startSync(id);
        return Map.of("taskId", taskId);
    }
}
