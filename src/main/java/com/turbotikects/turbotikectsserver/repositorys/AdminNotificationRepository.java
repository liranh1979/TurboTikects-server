package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AdminNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AdminNotificationRepository extends JpaRepository<AdminNotificationEntity, Long> {

    List<AdminNotificationEntity> findByIsReadFalseOrderByCreatedAtDesc();

    List<AdminNotificationEntity> findTop50ByOrderByCreatedAtDesc();

    long countByIsReadFalse();

    boolean existsByTicketIdAndFieldKeyAndNotificationType(Long ticketId, String fieldKey, String notificationType);

    @Modifying
    @Transactional
    @Query("UPDATE AdminNotificationEntity n SET n.isRead = true WHERE n.isRead = false")
    void markAllRead();

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminNotificationEntity n WHERE n.ticketId = :ticketId")
    void deleteByTicketId(@Param("ticketId") Long ticketId);
}
