package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketRepository extends JpaRepository<TicketEntity, Long>, JpaSpecificationExecutor<TicketEntity> {

    // JOIN with users so company filter works even if ticket.company_id is NULL
    // (e.g. ticket created before the user was assigned to a company)
    @Query(value = """
            SELECT t.* FROM tickets t
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:cursor IS NULL OR t.id < :cursor)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:templateId IS NULL OR t.template_id = :templateId)
              AND (:responsibleUserId IS NULL OR t.responsible_user_id = :responsibleUserId)
              AND (:filterUserId IS NULL OR t.request_user_id = :filterUserId)
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY t.id DESC LIMIT :size
            """, nativeQuery = true)
    List<TicketEntity> findPage(@Param("cursor") Long cursor, @Param("size") int size, @Param("status") String status, @Param("priority") String priority, @Param("templateId") Long templateId, @Param("responsibleUserId") Integer responsibleUserId, @Param("filterUserId") Integer filterUserId, @Param("companyId") Integer companyId);

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

    // LIKE-based (not FULLTEXT) so short/numeric queries like "1020" still match — the
    // MATCH...AGAINST index used by searchPage has a 3-char minimum token size and only
    // covers title/description text, not the ticket id itself. Used by the relationship
    // Link/Merge ticket-picker combobox, which needs fast partial/numeric-id matches.
    @Query(value = """
            SELECT t.* FROM tickets t
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE t.id <> :excludeId
              AND (t.id = :idMatch OR t.title LIKE CONCAT('%', :q, '%'))
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY t.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<TicketEntity> searchForPicker(@Param("q") String q, @Param("idMatch") Long idMatch,
                                        @Param("excludeId") Long excludeId, @Param("companyId") Integer companyId,
                                        @Param("limit") int limit);

    // Known Error suggestion lookup (Problem Management): any ticket flagged known_error=true
    // in its ticketData JSON, matched against the same title/description FULLTEXT index
    // searchPage already uses — deliberately not scoped to a specific template id/name, so any
    // future problem-shaped template is picked up automatically. See
    // V2/Problem Management/02-relationships-known-error.html.
    @Query(value = """
            SELECT t.* FROM tickets t
            WHERE MATCH(t.title, t.description) AGAINST (:q IN BOOLEAN MODE)
              AND JSON_EXTRACT(t.ticket_data, '$.known_error') = true
            ORDER BY t.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<TicketEntity> findKnownErrorMatches(@Param("q") String q, @Param("limit") int limit);

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

    // ── DASHBOARD AGGREGATES ─────────────────────────────────────────────────
    // Same company-scoping idiom as findPage/searchPage above (fallback to the
    // requester's users.company_id since tickets.company_id isn't always set).

    @Query(value = """
            SELECT DATE(t.created_at) AS bucket, COUNT(*) AS cnt
            FROM tickets t LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE t.created_at >= :since
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY DATE(t.created_at) ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> countCreatedByDaySince(@Param("since") LocalDateTime since, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT HOUR(t.created_at) AS bucket, COUNT(*) AS cnt
            FROM tickets t LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE t.created_at >= :since
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY HOUR(t.created_at) ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> countCreatedByHourSince(@Param("since") LocalDateTime since, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT t.status AS status, COUNT(*) AS cnt
            FROM tickets t LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY t.status
            """, nativeQuery = true)
    List<Object[]> countByStatus(@Param("companyId") Integer companyId);

    @Query(value = """
            SELECT COUNT(*) AS cnt, MAX(t.updated_at) AS last_update
            FROM tickets t LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            """, nativeQuery = true)
    List<Object[]> fingerprintData(@Param("companyId") Integer companyId);
}
