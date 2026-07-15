package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.ApprovalDecisionRequestDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowApprovalContextDto;
import com.turbotikects.turbotikectsserver.services.ApprovalService;
import org.springframework.web.bind.annotation.*;

// Public, unauthenticated — deliberately under /workflow-approval/** (not /workflow/**) so the
// wildcard exclusion in WebConfig.java can't accidentally make the authenticated /api/v1/workflow/**
// endpoints public too. Same reasoning as CsatController's own path split.
@RestController
@RequestMapping("/api/v1/workflow-approval")
public class WorkflowApprovalController {

    private final ApprovalService approvalService;

    public WorkflowApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/{token}")
    public WorkflowApprovalContextDto getContext(@PathVariable String token) {
        return approvalService.getContextByToken(token);
    }

    @PostMapping("/{token}")
    public void decide(@PathVariable String token, @RequestBody ApprovalDecisionRequestDto dto) {
        approvalService.recordDecisionByToken(token, dto.getDecision(), dto.getReason());
    }
}
