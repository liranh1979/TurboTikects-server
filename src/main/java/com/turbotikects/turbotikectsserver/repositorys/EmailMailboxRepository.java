package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EmailMailboxRepository extends JpaRepository<EmailMailboxEntity, Long> {

    List<EmailMailboxEntity> findByIsActiveTrue();

    List<EmailMailboxEntity> findByIsActiveTrueAndCanReceiveTrue();

    @Modifying
    @Transactional
    @Query("UPDATE EmailMailboxEntity m SET m.isDefaultSender = false")
    void clearAllDefaults();
}
