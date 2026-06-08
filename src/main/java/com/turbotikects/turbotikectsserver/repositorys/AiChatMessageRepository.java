package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AiChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, Long> {

    List<AiChatMessageEntity> findBySessionIdOrderByCreatedAt(Long sessionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AiChatMessageEntity m WHERE m.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
