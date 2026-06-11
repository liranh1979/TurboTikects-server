package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    Optional<GroupEntity> findByDisplayName(String displayName);

    java.util.List<GroupEntity> findByCompanyId(Integer companyId);

    long countByCompanyId(Integer companyId);
}
