package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiSettingsRepository extends JpaRepository<AiSettingsEntity,Long> {

    @Modifying // Tells Spring this is an update/delete operation ⚡
    @Transactional // Required for modifying queries 🔒
    @Query("UPDATE AiSettingsEntity a SET a.isActive = false")
    void deactivateAllSettings();


    @Modifying // Tells Spring this is an update/delete operation ⚡
    @Transactional // Required for modifying queries 🔒
    @Query("UPDATE AiSettingsEntity a SET a.isActive = true where a.id = :id")
    void deactivatedSettings(Long id);

}
