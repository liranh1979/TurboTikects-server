package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.EmailMessageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailMessageLogRepository extends JpaRepository<EmailMessageLogEntity, Long> {

    Optional<EmailMessageLogEntity> findByMessageId(String messageId);

    List<EmailMessageLogEntity> findByTicketId(Long ticketId);
}
