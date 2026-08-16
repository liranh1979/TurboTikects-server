package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.ReportScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportScheduleRepository extends JpaRepository<ReportScheduleEntity, Long> {
    Optional<ReportScheduleEntity> findByReportDefinitionId(Long reportDefinitionId);
    void deleteByReportDefinitionId(Long reportDefinitionId);

    // Active-ness is governed by report_definitions.is_active (joined in the service layer,
    // not here) rather than a second is_active flag on the schedule itself — see FEAT-05.1.
    List<ReportScheduleEntity> findByNextRunAtLessThanEqual(LocalDateTime now);
}
