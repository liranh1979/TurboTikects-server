package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    // JOIN with users so company filter works even if ticket.company_id is NULL
    // (e.g. ticket created before the user was assigned to a company)
    @Query(value = """
            SELECT t.* FROM tickets t
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:cursor IS NULL OR t.id < :cursor)
              AND (:status IS NULL OR t.status = :status)
              AND (:templateId IS NULL OR t.template_id = :templateId)
              AND (:responsibleUserId IS NULL OR t.responsible_user_id = :responsibleUserId)
              AND (:filterUserId IS NULL OR t.request_user_id = :filterUserId)
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY t.id DESC LIMIT :size
            """, nativeQuery = true)
    List<TicketEntity> findPage(@Param("cursor") Long cursor, @Param("size") int size, @Param("status") String status, @Param("templateId") Long templateId, @Param("responsibleUserId") Integer responsibleUserId, @Param("filterUserId") Integer filterUserId, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT t.* FROM tickets t
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE MATCH(t.title, t.description) AGAINST (:q IN BOOLEAN MODE)
              AND (:cursor IS NULL OR t.id < :cursor)
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY t.id DESC LIMIT :size
            """, nativeQuery = true)
    List<TicketEntity> searchPage(@Param("q") String q, @Param("cursor") Long cursor, @Param("size") int size, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT t.* FROM tickets t
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE MATCH(t.title, t.description) AGAINST (:q IN BOOLEAN MODE)
              AND (:cursor IS NULL OR t.id < :cursor)
              AND t.request_user_id = :filterUserId
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY t.id DESC LIMIT :size
            """, nativeQuery = true)
    List<TicketEntity> searchPageByUser(@Param("q") String q, @Param("cursor") Long cursor, @Param("size") int size, @Param("filterUserId") Integer filterUserId, @Param("companyId") Integer companyId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketEntity t WHERE t.id IN :ids")
    void deleteByIdIn(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.status = :status, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :ids")
    void updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.responsibleUserId = :userId, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :ids")
    void updateResponsibleUserByIds(@Param("ids") List<Long> ids, @Param("userId") Integer userId);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.responsibleGroupId = :groupId, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :ids")
    void updateResponsibleGroupByIds(@Param("ids") List<Long> ids, @Param("groupId") Integer groupId);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.companyId = :companyId WHERE t.requestUserId = :userId")
    void updateCompanyForUser(@Param("userId") Integer userId, @Param("companyId") Integer companyId);

    List<TicketEntity> findByStatusNotIn(List<String> statuses);
}
