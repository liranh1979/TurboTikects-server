package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AzureConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AzureConfigRepository extends JpaRepository<AzureConfigEntity, Long> {

    List<AzureConfigEntity> findByIsActiveTrue();
}
