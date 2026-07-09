package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketCsatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketCsatRepository extends JpaRepository<TicketCsatEntity, Integer> {

    Optional<TicketCsatEntity> findByToken(String token);

    Optional<TicketCsatEntity> findByTicketId(Long ticketId);

    boolean existsByTicketId(Long ticketId);

    // Same company-scoping idiom as TicketRepository.fingerprintData — fallback
    // to the requester's users.company_id since ticket_csat has no company_id itself.
    @Query(value = """
            SELECT COUNT(*) AS cnt, MAX(c.responded_at) AS last_response
            FROM ticket_csat c
            JOIN tickets t ON t.id = c.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            """, nativeQuery = true)
    List<Object[]> fingerprintData(@Param("companyId") Integer companyId);

    @Query(value = """
            SELECT c.score AS score, COUNT(*) AS cnt
            FROM ticket_csat c
            JOIN tickets t ON t.id = c.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE c.responded_at IS NOT NULL
              AND c.responded_at >= :since
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY c.score
            """, nativeQuery = true)
    List<Object[]> scoreDistributionSince(@Param("since") LocalDateTime since, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT COUNT(*) AS sent, SUM(CASE WHEN c.responded_at IS NOT NULL THEN 1 ELSE 0 END) AS responded
            FROM ticket_csat c
            JOIN tickets t ON t.id = c.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE c.sent_at >= :since
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            """, nativeQuery = true)
    List<Object[]> responseRateSince(@Param("since") LocalDateTime since, @Param("companyId") Integer companyId);

    @Query(value = """
            SELECT t.id AS ticket_id, t.title AS title, ru.display_name AS agent, c.score AS score, c.comment AS comment
            FROM ticket_csat c
            JOIN tickets t ON t.id = c.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            LEFT JOIN users ru ON ru.red_id = t.responsible_user_id
            WHERE c.responded_at IS NOT NULL
              AND c.score <= 2
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY c.responded_at DESC
            LIMIT 50
            """, nativeQuery = true)
    List<Object[]> lowScoreTickets(@Param("companyId") Integer companyId);

    @Query(value = """
            SELECT c.comment AS comment, c.score AS score
            FROM ticket_csat c
            JOIN tickets t ON t.id = c.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE c.responded_at IS NOT NULL
              AND c.score <= 2
              AND c.comment IS NOT NULL AND c.comment != ''
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            ORDER BY c.responded_at DESC
            LIMIT 50
            """, nativeQuery = true)
    List<Object[]> recentLowScoreComments(@Param("companyId") Integer companyId);
}
