package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AzureFieldMappingEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AzureFieldMappingRepository extends JpaRepository<AzureFieldMappingEntity, Long> {

    List<AzureFieldMappingEntity> findByAzureConfigId(Long configId);

    List<AzureFieldMappingEntity> findByAzureConfigIdAndEntityType(Long configId, String entityType);

    @Modifying
    @Transactional
    @Query("DELETE FROM AzureFieldMappingEntity m WHERE m.azureConfigId = :configId AND m.entityType = :entityType")
    void deleteByAzureConfigIdAndEntityType(Long configId, String entityType);
}
