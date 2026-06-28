package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.SslSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SslSettingsRepository extends JpaRepository<SslSettingsEntity, Integer> {
}
