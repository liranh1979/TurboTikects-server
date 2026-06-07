package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketUpdateQueueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketUpdateQueueRepository extends JpaRepository<TicketUpdateQueueEntity, Long> {

    @Query(value = "SELECT * FROM ticket_update_queue WHERE status = 'pending' ORDER BY id LIMIT :size FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<TicketUpdateQueueEntity> claimBatch(@Param("size") int size);

    @Modifying
    @Transactional
    @Query("UPDATE TicketUpdateQueueEntity q SET q.status = 'pending', q.claimedBy = NULL, q.claimedAt = NULL WHERE q.status = 'processing' AND q.claimedAt < :cutoff")
    int resetStuck(@Param("cutoff") LocalDateTime cutoff);
}
