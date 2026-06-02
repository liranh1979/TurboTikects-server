package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.SsoProviderDto;
import com.turbotikects.turbotikectsserver.repositorys.AzureConfigRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sso")
public class SsoController {

    private final AzureConfigRepository azureConfigRepository;

    public SsoController(AzureConfigRepository azureConfigRepository) {
        this.azureConfigRepository = azureConfigRepository;
    }

    @GetMapping("/providers")
    public List<SsoProviderDto> getProviders() {
        return azureConfigRepository.findBySsoEnabledTrue().stream()
                .map(c -> new SsoProviderDto(c.getId(), c.getDisplayName(), c.getSsoDisplayName(), "azure"))
                .toList();
    }
}
