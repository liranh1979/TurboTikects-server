package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.services.AiChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai-chat")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    // ── CREATE SESSION ────────────────────────────────────────────────────────

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public AiChatSessionDto createSession(@RequestBody AiChatCreateSessionDto dto,
                                          HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return aiChatService.createSession(userId, dto.getSessionType(), dto.getTicketId());
    }

    // ── LIST SESSIONS ─────────────────────────────────────────────────────────

    @GetMapping("/sessions")
    public List<AiChatSessionDto> getSessions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long ticketId,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (ticketId != null) {
            return aiChatService.getSessionsForTicket(ticketId);
        }
        return aiChatService.getSessions(userId, type);
    }

    // ── GET SESSION ───────────────────────────────────────────────────────────

    @GetMapping("/sessions/{id}")
    public AiChatSessionDto getSession(@PathVariable Long id, HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return aiChatService.getSession(id, userId);
    }

    // ── GET OR CREATE TICKET CONSULT ──────────────────────────────────────────

    @GetMapping("/sessions/ticket-consult/{ticketId}")
    public AiChatSessionDto getOrCreateTicketConsult(@PathVariable Long ticketId,
                                                      HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return aiChatService.getOrCreateTicketConsult(ticketId, userId);
    }

    // ── SEND MESSAGE ──────────────────────────────────────────────────────────

    @PostMapping("/sessions/{id}/messages")
    public AiChatMessageDto sendMessage(@PathVariable Long id,
                                         @RequestBody AiChatSendDto dto,
                                         HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        Integer userId = currentUserId(request);
        return aiChatService.sendMessage(id, userId, dto.getContent());
    }

    // ── ESCALATE TO TICKET ────────────────────────────────────────────────────

    @PostMapping("/sessions/{id}/escalate")
    public Map<String, Long> escalate(@PathVariable Long id, HttpServletRequest request)
            throws URISyntaxException, IOException, InterruptedException {
        Integer userId = currentUserId(request);
        Long ticketId = aiChatService.escalateToTicket(id, userId);
        return Map.of("ticketId", ticketId);
    }

    // ── SESSION HELPER ────────────────────────────────────────────────────────

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
