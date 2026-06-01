package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.repositorys.AzureConfigRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AzureSyncScheduler {

    private final AzureConfigRepository configRepository;
    private final AzureSyncService syncService;

    public AzureSyncScheduler(AzureConfigRepository configRepository, AzureSyncService syncService) {
        this.configRepository = configRepository;
        this.syncService = syncService;
    }

    @Scheduled(fixedRate = 86_400_000) // 24 hours
    public void syncAll() {
        configRepository.findByIsActiveTrue()
                .forEach(cfg -> syncService.startSync(cfg.getId()));
    }
}
