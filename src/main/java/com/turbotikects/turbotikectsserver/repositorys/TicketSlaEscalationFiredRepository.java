package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketSlaEscalationFiredEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketSlaEscalationFiredRepository extends JpaRepository<TicketSlaEscalationFiredEntity, Long> {

    boolean existsByTicketIdAndSlaEscalationStepId(Long ticketId, Long slaEscalationStepId);

    List<TicketSlaEscalationFiredEntity> findByTicketId(Long ticketId);
}
