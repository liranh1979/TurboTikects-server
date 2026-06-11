package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import com.turbotikects.turbotikectsserver.dto.TicketActivityLogDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.TicketAiService;
import com.turbotikects.turbotikectsserver.services.TicketService;
import com.turbotikects.turbotikectsserver.services.TicketSseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketAiService ticketAiService;
    private final TicketSseService ticketSseService;

    public TicketController(TicketService ticketService,
                            TicketAiService ticketAiService,
                            TicketSseService ticketSseService) {
        this.ticketService = ticketService;
        this.ticketAiService = ticketAiService;
        this.ticketSseService = ticketSseService;
    }

    // ── SSE STREAM ────────────────────────────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return ticketSseService.subscribe();
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    @GetMapping
    public List<TicketListItemDto> getPage(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long templateId,
            @RequestParam(name = "labels", required = false) List<Long> labelIds,
            @RequestParam(required = false) Integer responsibleUserId,
            HttpServletRequest request) {
        UserDto caller = currentUser(request);
        boolean isManager = caller != null
                && (caller.isSuperAdmin()
                    || (caller.getEffectivePermissions() != null
                        && caller.getEffectivePermissions().contains("TICKET_MANAGER")));
        Integer companyId = (caller != null && !caller.isSuperAdmin()) ? caller.getCompanyId() : null;
        // Company-scoped admins (any non-super-admin with a company assigned) see all tickets in
        // their company — do not restrict to their own tickets regardless of TICKET_MANAGER.
        boolean isCompanyScoped = companyId != null;
        Integer filterUserId = (isManager || isCompanyScoped) ? null
                : (caller != null ? caller.getUserId().intValue() : null);
        return ticketService.getPage(cursor, size, search, status, templateId, labelIds, responsibleUserId, filterUserId, companyId);
    }

    // ── GET BY ID ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public TicketDetailDto getById(@PathVariable Long id, HttpServletRequest request) {
        UserDto caller = currentUser(request);
        boolean isManager = caller != null && (caller.isSuperAdmin()
                || (caller.getEffectivePermissions() != null
                    && caller.getEffectivePermissions().contains("TICKET_MANAGER")));
        return ticketService.getById(id, caller != null ? caller.getUserId().intValue() : null, isManager);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetailDto create(@RequestBody CreateTicketRequestDto dto,
                                  HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ticketService.create(dto, userId);
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    public ResponseEntity<TicketDetailDto> patch(@PathVariable Long id,
                                                  @RequestBody PatchTicketRequestDto dto,
                                                  HttpServletRequest request) {
        Integer userId = currentUserId(request);
        try {
            return ResponseEntity.ok(ticketService.patch(id, dto, userId));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                TicketDetailDto current = ticketService.getById(id);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(current);
            }
            throw ex;
        }
    }

    // ── DELETE SINGLE ─────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @RequirePermission("TICKET_MANAGER")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ticketService.bulkDelete(List.of(id));
    }

    // ── BULK OPERATIONS ───────────────────────────────────────────────────────

    @PostMapping("/bulk-delete")
    @RequirePermission("TICKET_MANAGER")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkDelete(@RequestBody BulkTicketIdsDto dto) {
        ticketService.bulkDelete(dto.getIds());
    }

    @PostMapping("/bulk-update-status")
    @RequirePermission("TICKET_MANAGER")
    public Map<String, Integer> bulkUpdateStatus(@RequestBody BulkUpdateStatusDto dto,
                                                  HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ticketService.bulkUpdateStatus(dto.getIds(), dto.getStatus(), userId);
    }

    @PostMapping("/bulk-update-responsible")
    @RequirePermission("TICKET_MANAGER")
    public Map<String, Integer> bulkUpdateResponsible(@RequestBody BulkUpdateResponsibleDto dto,
                                                       HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ticketService.bulkUpdateResponsible(
                dto.getIds(), dto.getResponsibleUserId(), dto.getResponsibleGroupId(), userId);
    }

    @PostMapping("/bulk-add-label")
    @RequirePermission("TICKET_MANAGER")
    public Map<String, Integer> bulkAddLabel(@RequestBody BulkLabelDto dto,
                                              HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ticketService.bulkAddLabel(dto.getIds(), dto.getLabelId(), userId);
    }

    @PostMapping("/bulk-remove-label")
    @RequirePermission("TICKET_MANAGER")
    public Map<String, Integer> bulkRemoveLabel(@RequestBody BulkLabelDto dto,
                                                 HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ticketService.bulkRemoveLabel(dto.getIds(), dto.getLabelId(), userId);
    }

    // ── AI ANALYZE ────────────────────────────────────────────────────────────

    @PostMapping("/ai-analyze")
    public TicketAiAnalyzeResponseDto aiAnalyze(@RequestBody TicketAiAnalyzeRequestDto dto)
            throws URISyntaxException, IOException, InterruptedException {
        return ticketAiService.analyzeAndSuggest(dto.getProblem());
    }

    // ── PRESENCE ──────────────────────────────────────────────────────────────

    @PutMapping("/{id}/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePresence(@PathVariable Long id, HttpServletRequest request) {
        UserDto user = currentUser(request);
        if (user != null) {
            ticketService.updatePresence(id, user.getUserId().intValue(), user.getDisplayName());
        }
    }

    @DeleteMapping("/{id}/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePresence(@PathVariable Long id, HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId != null) {
            ticketService.removePresence(id, userId);
        }
    }

    @PostMapping("/{id}/presence/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void presenceTyping(@PathVariable Long id, HttpServletRequest request) {
        UserDto user = currentUser(request);
        if (user != null) {
            ticketService.presenceTyping(id, user.getUserId().intValue(), user.getDisplayName());
        }
    }

    // ── AI SOLUTION ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/activity/ai-solution")
    public ResponseEntity<Map<String, Object>> saveAiSolution(
            @PathVariable Long id,
            @RequestBody SaveAiSolutionRequestDto dto,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ResponseEntity.ok(ticketService.saveAiSolution(id, dto.getSolution(), userId));
    }

    @PostMapping("/{id}/activity/{activityId}/send-solution-email")
    public ResponseEntity<Map<String, Object>> sendSolutionEmail(
            @PathVariable Long id,
            @PathVariable Long activityId,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return ResponseEntity.ok(ticketService.sendSolutionEmail(id, activityId, userId));
    }

    // ── ACTIVITY LOG ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/activity-log")
    public List<TicketActivityLogEntity> getActivityLog(@PathVariable Long id) {
        return ticketService.getActivityLog(id);
    }

    @GetMapping("/{id}/activity")
    public org.springframework.data.domain.Page<TicketActivityLogDto> getActivityPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String type) {
        return ticketService.getActivityPaged(id, page, size, type);
    }

    // ── SESSION HELPERS ───────────────────────────────────────────────────────

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }

    private UserDto currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto) return dto;
        return null;
    }
}
