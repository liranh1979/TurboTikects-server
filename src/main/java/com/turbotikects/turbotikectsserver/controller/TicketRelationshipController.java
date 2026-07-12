package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.TicketRelationshipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketRelationshipController {

    private final TicketRelationshipService relationshipService;

    public TicketRelationshipController(TicketRelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    // ── LIST / LINK / UNLINK — authenticated only, no permission gate. ─────────
    // Matches the existing precedent set by PATCH /{id} and POST /{id}/clone:
    // any authenticated user who can reach a ticket id can act on it.

    @GetMapping("/{id}/relationships")
    public List<TicketRelationshipDto> list(@PathVariable Long id) {
        return relationshipService.list(id);
    }

    @PostMapping("/{id}/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketRelationshipDto link(@PathVariable Long id, @RequestBody LinkTicketRequestDto req,
                                       HttpServletRequest request) {
        UserDto caller = currentUser(request);
        Integer actorId = caller != null ? caller.getUserId().intValue() : null;
        return relationshipService.link(id, req, actorId);
    }

    @DeleteMapping("/{id}/relationships/{relationshipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long id, @PathVariable Long relationshipId, HttpServletRequest request) {
        UserDto caller = currentUser(request);
        Integer actorId = caller != null ? caller.getUserId().intValue() : null;
        relationshipService.unlink(id, relationshipId, actorId);
    }

    // ── MERGE — destructive (closes the source ticket), requires TICKET_MANAGER. ─

    @PostMapping("/{id}/merge")
    @RequirePermission("TICKET_MANAGER")
    public MergeResultDto merge(@PathVariable Long id, @RequestBody MergeTicketRequestDto req,
                                 HttpServletRequest request) {
        UserDto caller = currentUser(request);
        Integer actorId = caller != null ? caller.getUserId().intValue() : null;
        return relationshipService.merge(id, req, actorId);
    }

    // ── PICKER — ticket search for the Link/Merge dialogs' combobox. ───────────

    @GetMapping("/picker")
    public List<TicketPickerItemDto> picker(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long excludeId,
                                             @RequestParam(defaultValue = "8") int limit,
                                             HttpServletRequest request) {
        UserDto caller = currentUser(request);
        Integer companyId = (caller != null && !caller.isSuperAdmin()) ? caller.getCompanyId() : null;
        Long exclude = excludeId != null ? excludeId : -1L;
        return relationshipService.searchPicker(q, exclude, limit, companyId);
    }

    private UserDto currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto) return dto;
        return null;
    }
}
