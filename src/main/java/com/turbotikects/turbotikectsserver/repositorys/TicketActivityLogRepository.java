package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketActivityLogRepository extends JpaRepository<TicketActivityLogEntity, Long> {

    List<TicketActivityLogEntity> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    Page<TicketActivityLogEntity> findByTicketIdOrderByCreatedAtDesc(Long ticketId, Pageable pageable);

    Page<TicketActivityLogEntity> findByTicketIdAndActivityTypeOrderByCreatedAtDesc(
            Long ticketId, String activityType, Pageable pageable);
}
