package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.UserNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Long> {

    List<UserNotificationEntity> findTop50ByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    long countByRecipientUserIdAndIsReadFalse(Long recipientUserId);

    @Modifying
    @Transactional
    @Query("UPDATE UserNotificationEntity n SET n.isRead = true WHERE n.recipientUserId = :userId AND n.isRead = false")
    void markAllRead(@Param("userId") Long userId);
}
