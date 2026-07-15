package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.WorkflowApprovalDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowApprovalDecisionRepository extends JpaRepository<WorkflowApprovalDecisionEntity, Long> {

    // Ordered by id (insertion order), not level_order — after Phase 3's escalation, more than one
    // row can share a level_order (the original timed-out approver's row + the escalated-to
    // approver's row), so level_order alone is no longer a stable/unique ordering key.
    List<WorkflowApprovalDecisionEntity> findByWorkflowItemIdOrderById(Long workflowItemId);

    Optional<WorkflowApprovalDecisionEntity> findByWorkflowItemIdAndDecision(Long workflowItemId, String decision);

    List<WorkflowApprovalDecisionEntity> findByDecision(String decision);
}
