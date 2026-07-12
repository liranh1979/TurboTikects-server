package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> {
    List<AttachmentEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @Modifying
    @Transactional
    @Query("UPDATE AttachmentEntity a SET a.entityId = :targetId WHERE a.entityType = 'ticket' AND a.entityId = :sourceId")
    int reassignTicketAttachments(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
