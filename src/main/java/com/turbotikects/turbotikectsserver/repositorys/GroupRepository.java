package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    Optional<GroupEntity> findByDisplayName(String displayName);

    @Query(value = "SELECT * FROM user_groups WHERE company_id = :companyId", nativeQuery = true)
    java.util.List<GroupEntity> findByCompanyId(@Param("companyId") Integer companyId);

    @Query(value = "SELECT COUNT(*) FROM user_groups WHERE company_id = :companyId", nativeQuery = true)
    long countByCompanyId(@Param("companyId") Integer companyId);
}
