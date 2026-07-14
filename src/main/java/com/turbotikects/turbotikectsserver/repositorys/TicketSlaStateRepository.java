package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketSlaStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketSlaStateRepository extends JpaRepository<TicketSlaStateEntity, Long> {

    Optional<TicketSlaStateEntity> findByTicketId(Long ticketId);

    /** Tickets whose AI risk score has never been computed, or is stale relative to the given cutoff. */
    List<TicketSlaStateEntity> findByAiBreachRiskScoreIsNullOrAiLastScoredAtBefore(LocalDateTime staleBefore);

    // Same company-scoping idiom as TicketCsatRepository.fingerprintData — fallback to the
    // requester's users.company_id since ticket_sla_state has no company_id of its own.
    @Query(value = """
            SELECT COUNT(*) AS cnt, MAX(s.updated_at) AS last_update
            FROM ticket_sla_state s
            JOIN tickets t ON t.id = s.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            """, nativeQuery = true)
    List<Object[]> fingerprintData(@Param("companyId") Integer companyId);

    @Query(value = """
            SELECT t.priority AS priority,
                   COUNT(*) AS total,
                   SUM(CASE WHEN s.resolution_at IS NOT NULL THEN 1 ELSE 0 END) AS resolved_count,
                   SUM(CASE WHEN s.first_response_breached = 1 THEN 1 ELSE 0 END) AS fr_breached,
                   SUM(CASE WHEN s.resolution_breached = 1 THEN 1 ELSE 0 END) AS res_breached
            FROM ticket_sla_state s
            JOIN tickets t ON t.id = s.ticket_id
            LEFT JOIN users u ON u.red_id = t.request_user_id
            WHERE s.sla_policy_id IS NOT NULL
              AND (:companyId IS NULL OR t.company_id = :companyId OR u.company_id = :companyId)
            GROUP BY t.priority
            """, nativeQuery = true)
    List<Object[]> breachStatsByPriority(@Param("companyId") Integer companyId);
}
