package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketPresenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketPresenceRepository extends JpaRepository<TicketPresenceEntity, TicketPresenceEntity.TicketPresenceId> {

    List<TicketPresenceEntity> findByTicketId(Long ticketId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketPresenceEntity p WHERE p.ticketId = :ticketId AND p.userId = :userId")
    void deleteByTicketIdAndUserId(@Param("ticketId") Long ticketId, @Param("userId") Integer userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketPresenceEntity p WHERE p.lastSeen < :cutoff")
    void deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
