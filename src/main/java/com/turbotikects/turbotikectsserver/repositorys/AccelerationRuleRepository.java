package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AccelerationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccelerationRuleRepository extends JpaRepository<AccelerationRuleEntity, Long> {

    List<AccelerationRuleEntity> findAllByOrderByExecutionOrderAsc();

    List<AccelerationRuleEntity> findByIsActiveTrueOrderByExecutionOrderAsc();
}
