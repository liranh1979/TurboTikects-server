package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.ApprovalDecisionRequestDto;
import com.turbotikects.turbotikectsserver.dto.PatchWorkflowItemDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowItemContextDto;
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

    /**
     * Update status, assignee, and/or field values on an item — used by admins for any item, and
     * now also by an assigned end user filling in a Simple action item's own mini-fields
     * (ActionItemPage.tsx). Guarded by assertCanViewItem — previously any authenticated session
     * could patch any item at all.
     */
    @PatchMapping("/workflow/items/{id}")
    public WorkflowItemDto patchItem(@PathVariable Long id,
                                     @RequestBody PatchWorkflowItemDto dto,
                                     HttpServletRequest request) {
        UserDto caller = currentUser(request);
        workflowService.assertCanViewItem(id, currentUserId(request), isManager(caller), caller != null && caller.isSuperAdmin());
        return workflowService.patchItem(id, dto, currentUserId(request));
    }

    /** End user: change status only. Guarded by assertCanViewItem, same reasoning as patchItem above. */
    @PatchMapping("/workflow/items/{id}/status")
    public WorkflowItemDto patchItemStatus(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        UserDto caller = currentUser(request);
        workflowService.assertCanViewItem(id, currentUserId(request), isManager(caller), caller != null && caller.isSuperAdmin());
        return workflowService.patchItemStatus(id, body.get("status"), currentUserId(request));
    }

    /**
     * Manager-only: manually re-runs a completed/blocked external_api or mcp_tool action item —
     * e.g. after fixing a bad JSONPath or ticket data that made the original run fail/capture
     * nothing. Gated tighter than assertCanViewItem's read/assignee rules since this re-triggers a
     * real external call, not just a view.
     */
    @PostMapping("/workflow/items/{id}/retry")
    public WorkflowItemDto retryItem(@PathVariable Long id, HttpServletRequest request) {
        UserDto caller = currentUser(request);
        if (!isManager(caller)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Only a manager can retry a workflow action");
        }
        return workflowService.retryItem(id);
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

    /**
     * Full level-by-level decision history for an approval item — used by Phase 3's chain-progress
     * UI, and by the item-scoped view for a non-requester/non-manager approver. Guarded by
     * assertCanViewItem — previously any authenticated session could read any item's decisions.
     */
    @GetMapping("/workflow/items/{id}/approval-decisions")
    public List<com.turbotikects.turbotikectsserver.entitys.WorkflowApprovalDecisionEntity> getApprovalDecisions(
            @PathVariable Long id, HttpServletRequest request) {
        UserDto caller = currentUser(request);
        workflowService.assertCanViewItem(id, currentUserId(request), isManager(caller), caller != null && caller.isSuperAdmin());
        return approvalService.getDecisions(id);
    }

    /**
     * Minimal, ticket-detail-free context for a single item — lets a user assigned to just this
     * item (not the ticket's requester, not a TICKET_MANAGER) see enough to act on it without
     * being able to load the full ticket via GET /tickets/{id}.
     */
    @GetMapping("/workflow/items/{id}/context")
    public WorkflowItemContextDto getItemContext(@PathVariable Long id, HttpServletRequest request) {
        UserDto caller = currentUser(request);
        return workflowService.getItemContext(id, currentUserId(request), isManager(caller), caller != null && caller.isSuperAdmin());
    }

    private boolean isManager(UserDto caller) {
        return caller != null && (caller.isSuperAdmin()
                || (caller.getEffectivePermissions() != null && caller.getEffectivePermissions().contains("TICKET_MANAGER")));
    }

    private UserDto currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        return user instanceof UserDto dto ? dto : null;
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }
}
