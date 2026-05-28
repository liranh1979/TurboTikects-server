package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    List<GroupMemberEntity> findByGroupId(Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    long countByGroupId(Long groupId);

    List<GroupMemberEntity> findByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GroupMemberEntity m WHERE m.groupId = :groupId AND m.userId IN :userIds")
    void deleteByGroupIdAndUserIdIn(@Param("groupId") Long groupId, @Param("userIds") List<Long> userIds);
}
