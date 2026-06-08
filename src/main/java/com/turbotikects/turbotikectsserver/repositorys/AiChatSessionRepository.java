package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AiChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiChatSessionRepository extends JpaRepository<AiChatSessionEntity, Long> {

    List<AiChatSessionEntity> findByUserIdOrderByCreatedAtDesc(Integer userId);

    List<AiChatSessionEntity> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    List<AiChatSessionEntity> findByUserIdAndSessionTypeOrderByCreatedAtDesc(Integer userId, String sessionType);

    Optional<AiChatSessionEntity> findFirstByTicketIdAndSessionTypeOrderByCreatedAtDesc(Long ticketId, String sessionType);
}
