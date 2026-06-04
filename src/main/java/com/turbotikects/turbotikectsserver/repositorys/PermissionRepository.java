package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    List<PermissionEntity> findAllByOrderByDisplayOrder();

    Optional<PermissionEntity> findByPermissionKey(String permissionKey);

    @Query("SELECT DISTINCT p.permissionKey FROM PermissionEntity p WHERE " +
           "p.id IN (SELECT up.permissionId FROM UserPermissionEntity up WHERE up.userId = :userId) " +
           "OR p.id IN (SELECT gp.permissionId FROM GroupPermissionEntity gp " +
           "            WHERE gp.groupId IN (SELECT gm.groupId FROM GroupMemberEntity gm WHERE gm.userId = :userId))")
    List<String> findEffectivePermissionKeysByUserId(@Param("userId") Long userId);
}
