package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // ── DASHBOARD AGGREGATES ─────────────────────────────────────────────────
    // workflow_items has no company_id of its own — scoped by joining through tickets,
    // same company-scoping idiom as TicketRepository.

    @Query(value = """
            SELECT DATE(w.created_at) AS bucket, COUNT(*) AS cnt
            FROM workflow_items w JOIN tickets t ON t.id = w.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE w.created_at >= :since
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY DATE(w.created_at) ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> countCreatedByDaySince(@Param("since") LocalDateTime since, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT HOUR(w.created_at) AS bucket, COUNT(*) AS cnt
            FROM workflow_items w JOIN tickets t ON t.id = w.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE w.created_at >= :since
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY HOUR(w.created_at) ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> countCreatedByHourSince(@Param("since") LocalDateTime since, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT w.status AS status, COUNT(*) AS cnt
            FROM workflow_items w JOIN tickets t ON t.id = w.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY w.status
            """, nativeQuery = true)
    List<Object[]> countByStatus(@Param("companyId") Integer companyId);

    @Query(value = """
            SELECT COUNT(*) AS cnt, MAX(w.updated_at) AS last_update
            FROM workflow_items w JOIN tickets t ON t.id = w.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            """, nativeQuery = true)
    List<Object[]> fingerprintData(@Param("companyId") Integer companyId);
}
