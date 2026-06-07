package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketLabelAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketLabelAssignmentRepository extends JpaRepository<TicketLabelAssignmentEntity, Long> {

    List<TicketLabelAssignmentEntity> findByTicketIdIn(List<Long> ticketIds);

    List<TicketLabelAssignmentEntity> findByTicketId(Long ticketId);

    void deleteByTicketId(Long ticketId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketLabelAssignmentEntity a WHERE a.ticketId IN :ticketIds AND a.labelId = :labelId")
    void deleteByTicketIdsAndLabelId(@Param("ticketIds") List<Long> ticketIds, @Param("labelId") Long labelId);
}
