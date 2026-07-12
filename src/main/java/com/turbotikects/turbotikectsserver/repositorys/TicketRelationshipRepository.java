package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketRelationshipEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRelationshipRepository extends JpaRepository<TicketRelationshipEntity, Long> {

    List<TicketRelationshipEntity> findBySourceTicketIdOrderByCreatedAtDesc(Long ticketId);

    boolean existsBySourceTicketIdAndTargetTicketIdAndRelationshipType(
            Long sourceTicketId, Long targetTicketId, String relationshipType);

    Optional<TicketRelationshipEntity> findBySourceTicketIdAndTargetTicketIdAndRelationshipType(
            Long sourceTicketId, Long targetTicketId, String relationshipType);
}
