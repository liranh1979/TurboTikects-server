package com.turbotikects.turbotikectsserver.config;

import com.turbotikects.turbotikectsserver.repositorys.LdapConfigRepository;
import com.turbotikects.turbotikectsserver.services.LdapSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LdapSyncScheduler {

    private final LdapSyncService ldapSyncService;
    private final LdapConfigRepository ldapConfigRepository;

    public LdapSyncScheduler(LdapSyncService ldapSyncService,
                              LdapConfigRepository ldapConfigRepository) {
        this.ldapSyncService = ldapSyncService;
        this.ldapConfigRepository = ldapConfigRepository;
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000L)
    public void scheduledSync() {
        ldapConfigRepository.findByIsActiveTrue().forEach(config ->
                ldapSyncService.startSync(config.getId()));
    }
}
