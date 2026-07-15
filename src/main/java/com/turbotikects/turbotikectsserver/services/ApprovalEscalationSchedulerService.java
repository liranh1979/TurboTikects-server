package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.WorkflowApprovalDecisionEntity;
import com.turbotikects.turbotikectsserver.repositorys.WorkflowApprovalDecisionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FEAT-06 Phase 3 — checks every pending approval decision against its level's configured
 * timeoutHours and escalates it (via ApprovalService.checkAndEscalateIfTimedOut) once due.
 *
 * Mirrors SlaSchedulerService's established @Scheduled + @EventListener(ApplicationReadyEvent) +
 * synchronized shape. The startup catch-up runs on its own background thread rather than inline —
 * this is a deliberate fix informed by a real bug caught live in the Dashboard background-caching
 * feature: running startup work synchronously inside an @EventListener(ApplicationReadyEvent.class)
 * method blocks Spring's synchronous, sequential dispatch to every OTHER @EventListener on the same
 * (main) thread, so a slow catch-up pass here would have delayed sibling listeners (e.g.
 * SlaSchedulerService's own startup catch-up) for no reason. This pass is cheap (no network calls,
 * pure DB reads/writes) so the risk is small either way, but there's no reason to reintroduce a
 * once-already-proven footgun.
 */
@Slf4j
@Service
public class ApprovalEscalationSchedulerService {

    private final WorkflowApprovalDecisionRepository decisionRepo;
    private final ApprovalService approvalService;

    public ApprovalEscalationSchedulerService(WorkflowApprovalDecisionRepository decisionRepo, ApprovalService approvalService) {
        this.decisionRepo = decisionRepo;
        this.approvalService = approvalService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void tick() {
        try {
            checkTimeouts();
        } catch (Exception e) {
            log.error("[ApprovalEscalation] Scheduler tick failed", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[ApprovalEscalation] Running startup catch-up timeout check");
        new Thread(() -> {
            try {
                checkTimeouts();
            } catch (Exception e) {
                log.error("[ApprovalEscalation] Startup catch-up timeout check failed", e);
            }
        }, "approval-escalation-startup-catchup").start();
    }

    public synchronized void checkTimeouts() {
        List<WorkflowApprovalDecisionEntity> pending = decisionRepo.findByDecision("pending");
        for (WorkflowApprovalDecisionEntity decision : pending) {
            try {
                approvalService.checkAndEscalateIfTimedOut(decision);
            } catch (Exception e) {
                log.error("[ApprovalEscalation] Timeout check failed for decision {}", decision.getId(), e);
            }
        }
    }
}
