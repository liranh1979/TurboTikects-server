package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WorkflowItemRepository extends JpaRepository<WorkflowItemEntity, Long> {

    List<WorkflowItemEntity> findByTicketIdOrderByDisplayOrder(Long ticketId);

    List<WorkflowItemEntity> findByParentItemId(Long parentItemId);

    List<WorkflowItemEntity> findByAssignedUserId(Integer userId);

    List<WorkflowItemEntity> findByAssignedGroupIdIn(List<Integer> groupIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM WorkflowItemEntity w WHERE w.ticketId = :ticketId")
    void deleteByTicketId(@Param("ticketId") Long ticketId);
}
