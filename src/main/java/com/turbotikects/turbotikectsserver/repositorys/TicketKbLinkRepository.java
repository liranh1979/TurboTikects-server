package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketKbLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TicketKbLinkRepository extends JpaRepository<TicketKbLinkEntity, Long> {
    List<TicketKbLinkEntity> findByTicketId(Long ticketId);
    Optional<TicketKbLinkEntity> findByTicketIdAndArticleId(Long ticketId, Long articleId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketKbLinkEntity l WHERE l.ticketId = :ticketId AND l.articleId = :articleId")
    void deleteByTicketIdAndArticleId(@Param("ticketId") Long ticketId, @Param("articleId") Long articleId);
}
