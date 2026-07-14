package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.SlaEscalationStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlaEscalationStepRepository extends JpaRepository<SlaEscalationStepEntity, Long> {

    List<SlaEscalationStepEntity> findBySlaPolicyIdOrderByStepOrderAsc(Long slaPolicyId);

    List<SlaEscalationStepEntity> findBySlaPolicyIdInOrderByStepOrderAsc(List<Long> slaPolicyIds);

    void deleteBySlaPolicyId(Long slaPolicyId);
}
