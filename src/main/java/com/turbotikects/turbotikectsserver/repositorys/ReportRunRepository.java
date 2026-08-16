package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.ReportRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRunRepository extends JpaRepository<ReportRunEntity, Long> {
    Optional<ReportRunEntity> findFirstByReportDefinitionIdOrderByStartedAtDesc(Long reportDefinitionId);
    List<ReportRunEntity> findByReportDefinitionIdOrderByStartedAtDesc(Long reportDefinitionId);
}
