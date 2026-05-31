package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.LdapFieldMappingEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LdapFieldMappingRepository extends JpaRepository<LdapFieldMappingEntity, Long> {

    List<LdapFieldMappingEntity> findByLdapConfigId(Long configId);

    List<LdapFieldMappingEntity> findByLdapConfigIdAndEntityType(Long configId, String entityType);

    @Modifying
    @Transactional
    @Query("DELETE FROM LdapFieldMappingEntity m WHERE m.ldapConfigId = :configId AND m.entityType = :entityType")
    void deleteByLdapConfigIdAndEntityType(Long configId, String entityType);
}
