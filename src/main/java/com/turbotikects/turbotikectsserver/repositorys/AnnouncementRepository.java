package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long> {
    List<AnnouncementEntity> findByIsActiveTrueOrderByCreatedAtDesc();
    List<AnnouncementEntity> findAllByOrderByCreatedAtDesc();
}
