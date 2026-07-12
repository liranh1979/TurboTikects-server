package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketActivityLogRepository extends JpaRepository<TicketActivityLogEntity, Long> {

    List<TicketActivityLogEntity> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    Page<TicketActivityLogEntity> findByTicketIdOrderByCreatedAtDesc(Long ticketId, Pageable pageable);

    Page<TicketActivityLogEntity> findByTicketIdAndActivityTypeOrderByCreatedAtDesc(
            Long ticketId, String activityType, Pageable pageable);

    /**
     * Copies (not moves) activity rows from source to target during a ticket merge —
     * the source ticket keeps its own history intact since it still exists after merge.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO ticket_activity_log (ticket_id, actor_id, operation, activity_type, changes, metadata, created_at) " +
                   "SELECT :targetId, actor_id, operation, activity_type, changes, metadata, created_at " +
                   "FROM ticket_activity_log WHERE ticket_id = :sourceId", nativeQuery = true)
    int copyActivityLogRows(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
