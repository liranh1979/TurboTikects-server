package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.ReportDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinitionEntity, Long> {
    List<ReportDefinitionEntity> findAllByOrderByNameAsc();
}
