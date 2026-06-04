package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.GroupPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GroupPermissionRepository extends JpaRepository<GroupPermissionEntity, Long> {

    List<GroupPermissionEntity> findByGroupId(Long groupId);

    List<GroupPermissionEntity> findByGroupIdIn(List<Long> groupIds);

    List<GroupPermissionEntity> findByPermissionId(Long permissionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GroupPermissionEntity gp WHERE gp.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);
}
