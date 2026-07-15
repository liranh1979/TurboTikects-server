package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.ApprovalDecisionRequestDto;
import com.turbotikects.turbotikectsserver.dto.PatchWorkflowItemDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowItemDto;
import com.turbotikects.turbotikectsserver.services.ApprovalService;
import com.turbotikects.turbotikectsserver.services.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ApprovalService approvalService;

    public WorkflowController(WorkflowService workflowService, ApprovalService approvalService) {
        this.workflowService = workflowService;
        this.approvalService = approvalService;
    }

    /** All workflow items for a ticket (admin/manager view). */
    @GetMapping("/tickets/{ticketId}/workflow")
    public List<WorkflowItemDto> getWorkflowItems(@PathVariable Long ticketId) {
        return workflowService.getItems(ticketId);
    }

    /** Workflow items assigned to the calling user (end-user inbox). */
    @GetMapping("/workflow/my-items")
    public List<WorkflowItemDto> getMyItems(HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return workflowService.getItemsForUser(userId);
    }

    /** Admin: update status, assignee, and/or field values on any item. */
    @PatchMapping("/workflow/items/{id}")
    public WorkflowItemDto patchItem(@PathVariable Long id,
                                     @RequestBody PatchWorkflowItemDto dto,
                                     HttpServletRequest request) {
        return workflowService.patchItem(id, dto, currentUserId(request));
    }

    /** End user: change status only. */
    @PatchMapping("/workflow/items/{id}/status")
    public WorkflowItemDto patchItemStatus(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        return workflowService.patchItemStatus(id, body.get("status"), currentUserId(request));
    }

    /**
     * Approver (in-app path): resolves the item's current pending approval level. The one-click
     * email token flow (FEAT-06 Phase 2) will call the same ApprovalService.recordDecision from a
     * separate public, no-auth controller — this one requires a session, for an approver acting
     * from inside the app instead of an email link.
     */
    @PostMapping("/workflow/items/{id}/approval-decision")
    public void recordApprovalDecision(@PathVariable Long id,
                                        @RequestBody ApprovalDecisionRequestDto dto,
                                        HttpServletRequest request) {
        approvalService.recordDecision(id, dto.getDecision(), dto.getReason(), currentUserId(request));
    }

    /** Full level-by-level decision history for an approval item — used by Phase 3's chain-progress UI. */
    @GetMapping("/workflow/items/{id}/approval-decisions")
    public List<com.turbotikects.turbotikectsserver.entitys.WorkflowApprovalDecisionEntity> getApprovalDecisions(@PathVariable Long id) {
        return approvalService.getDecisions(id);
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }
}
