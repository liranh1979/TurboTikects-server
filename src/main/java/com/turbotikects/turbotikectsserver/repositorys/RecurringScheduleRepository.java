package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.RecurringScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RecurringScheduleRepository extends JpaRepository<RecurringScheduleEntity, Long> {
    List<RecurringScheduleEntity> findByIsActiveTrueAndNextRunAtLessThanEqual(LocalDateTime now);
    List<RecurringScheduleEntity> findAllByOrderByNameAsc();
}
