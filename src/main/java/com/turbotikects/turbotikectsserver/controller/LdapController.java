package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.LdapMappingService;
import com.turbotikects.turbotikectsserver.services.LdapService;
import com.turbotikects.turbotikectsserver.services.LdapSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ldap")
@RequirePermission("MANAGE_LDAP")
public class LdapController {

    private final LdapService ldapService;
    private final LdapMappingService ldapMappingService;
    private final LdapSyncService ldapSyncService;

    public LdapController(LdapService ldapService,
                          LdapMappingService ldapMappingService,
                          LdapSyncService ldapSyncService) {
        this.ldapService = ldapService;
        this.ldapMappingService = ldapMappingService;
        this.ldapSyncService = ldapSyncService;
    }

    @GetMapping("/configs")
    public List<LdapConfigDto> getAllConfigs() {
        return ldapService.getAllConfigs();
    }

    @PostMapping("/configs")
    public LdapConfigDto createConfig(@RequestBody LdapConfigDto dto) {
        return ldapService.createConfig(dto);
    }

    @PatchMapping("/configs/{id}")
    public LdapConfigDto updateConfig(@PathVariable Long id, @RequestBody LdapConfigDto dto) {
        return ldapService.updateConfig(id, dto);
    }

    @DeleteMapping("/configs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable Long id) {
        ldapService.deleteConfig(id);
    }

    @PostMapping("/configs/test")
    public LdapTestResultDto testConnectionRaw(@RequestBody LdapConfigDto dto) {
        return ldapService.testConnectionRaw(dto);
    }

    @PostMapping("/configs/{id}/test")
    public LdapTestResultDto testConnection(@PathVariable Long id) {
        return ldapService.testConnection(id);
    }

    @PostMapping("/configs/{id}/activate")
    public LdapConfigDto activateConfig(@PathVariable Long id,
                                        @RequestParam(defaultValue = "true") boolean active) {
        return ldapService.setActive(id, active);
    }

    @GetMapping("/configs/{id}/sample-user")
    public LdapSampleDto sampleUser(@PathVariable Long id) {
        return ldapService.fetchSampleUser(id);
    }

    @GetMapping("/configs/{id}/sample-group")
    public LdapSampleDto sampleGroup(@PathVariable Long id) {
        return ldapService.fetchSampleGroup(id);
    }

    @PostMapping("/configs/{id}/suggest-mapping")
    public LdapAiMappingResultDto suggestMapping(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "user") String entityType) {
        return ldapMappingService.suggestMappings(id, entityType);
    }

    @GetMapping("/configs/{id}/mappings")
    public Object getMappings(@PathVariable Long id,
                              @RequestParam(required = false) String entityType) {
        if (entityType != null) {
            return ldapService.getMappings(id, entityType);
        }
        return ldapService.getAllMappings(id);
    }

    @PostMapping("/configs/{id}/mappings")
    public List<LdapFieldMappingDto> saveMappings(@PathVariable Long id,
                                                   @RequestBody SaveLdapMappingsRequestDto dto) {
        return ldapService.saveMappings(id, dto);
    }

    @PostMapping("/configs/{id}/sync")
    public Map<String, String> triggerSync(@PathVariable Long id) {
        String taskId = ldapSyncService.startSync(id);
        return Map.of("taskId", taskId);
    }
}
