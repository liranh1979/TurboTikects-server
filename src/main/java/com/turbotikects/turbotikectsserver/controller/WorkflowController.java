package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.PatchWorkflowItemDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowItemDto;
import com.turbotikects.turbotikectsserver.services.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
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

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }
}
