package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.SetupStatusDto;
import com.turbotikects.turbotikectsserver.repositorys.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    @Autowired private AiSettingsRepository     aiSettingsRepository;
    @Autowired private TemplateRepository       templateRepository;
    @Autowired private EmailMailboxRepository   emailMailboxRepository;
    @Autowired private UserRepository           userRepository;
    @Autowired private AzureConfigRepository    azureConfigRepository;
    @Autowired private LdapConfigRepository     ldapConfigRepository;
    @Autowired private SslSettingsRepository    sslSettingsRepository;

    @GetMapping("/status")
    public SetupStatusDto getStatus() {
        boolean aiConfigured       = !aiSettingsRepository.findByIsActive(true).isEmpty();
        boolean templateConfigured = templateRepository.count() > 0;
        boolean emailConfigured    = emailMailboxRepository.count() > 0;
        boolean usersConfigured    = userRepository.count() > 1;
        boolean ssoConfigured      = !azureConfigRepository.findBySsoEnabledTrue().isEmpty()
                                  || !ldapConfigRepository.findByIsActiveTrue().isEmpty();
        boolean sslConfigured      = sslSettingsRepository.findById(1)
                                        .map(e -> e.isEnabled()).orElse(false);
        return new SetupStatusDto(aiConfigured, templateConfigured, emailConfigured,
                                  usersConfigured, ssoConfigured, sslConfigured);
    }
}
