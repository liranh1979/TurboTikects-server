package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.UserPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserPermissionRepository extends JpaRepository<UserPermissionEntity, Long> {

    List<UserPermissionEntity> findByUserId(Long userId);

    List<UserPermissionEntity> findByUserIdIn(List<Long> userIds);

    List<UserPermissionEntity> findByPermissionId(Long permissionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserPermissionEntity up WHERE up.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
