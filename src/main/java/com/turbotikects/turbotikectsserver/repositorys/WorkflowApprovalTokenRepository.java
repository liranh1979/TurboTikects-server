package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.WorkflowApprovalTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowApprovalTokenRepository extends JpaRepository<WorkflowApprovalTokenEntity, Long> {

    Optional<WorkflowApprovalTokenEntity> findByToken(String token);
}
