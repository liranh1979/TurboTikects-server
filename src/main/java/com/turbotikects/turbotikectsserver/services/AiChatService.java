package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiChatService {

    private final AiChatSessionRepository sessionRepo;
    private final AiChatMessageRepository messageRepo;
    private final AiSettingsService aiSettingsService;
    private final TicketRepository ticketRepo;
    private final TicketAiService ticketAiService;
    private final TicketService ticketService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiChatService(AiChatSessionRepository sessionRepo,
                         AiChatMessageRepository messageRepo,
                         AiSettingsService aiSettingsService,
                         TicketRepository ticketRepo,
                         TicketAiService ticketAiService,
                         TicketService ticketService) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.aiSettingsService = aiSettingsService;
        this.ticketRepo  = ticketRepo;
        this.ticketAiService = ticketAiService;
        this.ticketService = ticketService;
    }

    // ── CREATE SESSION ────────────────────────────────────────────────────────

    @Transactional
    public AiChatSessionDto createSession(Integer userId, String sessionType, Long ticketId) {
        AiChatSessionEntity session = new AiChatSessionEntity();
        session.setUserId(userId);
        session.setSessionType(sessionType != null ? sessionType : "user_help");
        session.setStatus("active");
        session.setTicketId(ticketId);

        if ("ticket_consult".equals(session.getSessionType()) && ticketId != null) {
            ticketRepo.findById(ticketId).ifPresent(t ->
                    session.setTitle("Consulting on: " + t.getTitle()));
        }

        AiChatSessionEntity saved = sessionRepo.save(session);
        return toSessionDto(saved, Collections.emptyList());
    }

    // ── LIST SESSIONS ─────────────────────────────────────────────────────────

    public List<AiChatSessionDto> getSessions(Integer userId, String sessionType) {
        List<AiChatSessionEntity> sessions = sessionType != null
                ? sessionRepo.findByUserIdAndSessionTypeOrderByCreatedAtDesc(userId, sessionType)
                : sessionRepo.findByUserIdOrderByCreatedAtDesc(userId);
        return sessions.stream()
                .map(s -> toSessionDto(s, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    public List<AiChatSessionDto> getSessionsForTicket(Long ticketId) {
        return sessionRepo.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(s -> toSessionDto(s, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    // ── GET SESSION WITH MESSAGES ─────────────────────────────────────────────

    public AiChatSessionDto getSession(Long sessionId, Integer userId) {
        AiChatSessionEntity session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        List<AiChatMessageEntity> messages = messageRepo.findBySessionIdOrderByCreatedAt(sessionId);
        return toSessionDto(session, messages);
    }

    // ── GET OR CREATE TICKET CONSULT SESSION ──────────────────────────────────

    @Transactional
    public AiChatSessionDto getOrCreateTicketConsult(Long ticketId, Integer userId) {
        Optional<AiChatSessionEntity> existing =
                sessionRepo.findFirstByTicketIdAndSessionTypeOrderByCreatedAtDesc(ticketId, "ticket_consult");

        if (existing.isPresent()) {
            AiChatSessionEntity s = existing.get();
            List<AiChatMessageEntity> messages = messageRepo.findBySessionIdOrderByCreatedAt(s.getId());
            return toSessionDto(s, messages);
        }
        return createSession(userId, "ticket_consult", ticketId);
    }

    // ── SEND MESSAGE ──────────────────────────────────────────────────────────

    @Transactional
    public AiChatMessageDto sendMessage(Long sessionId, Integer userId, String content)
            throws IOException, URISyntaxException, InterruptedException {

        AiChatSessionEntity session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No AI configured");

        // Save user message
        AiChatMessageEntity userMsg = new AiChatMessageEntity();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        messageRepo.save(userMsg);

        // Auto-title on first message
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(content.length() > 60 ? content.substring(0, 57) + "…" : content);
            sessionRepo.save(session);
        }

        // Build conversation history as LlmStructure list
        List<AiChatMessageEntity> history = messageRepo.findBySessionIdOrderByCreatedAt(sessionId);
        List<LlmStructure> llmRequest = new ArrayList<>();

        LlmStructure systemMsg = new LlmStructure();
        systemMsg.setRole("system");
        systemMsg.setContent(buildSystemPrompt(session));
        llmRequest.add(systemMsg);

        for (AiChatMessageEntity m : history) {
            if ("system".equals(m.getRole())) continue;
            LlmStructure s = new LlmStructure();
            s.setRole(m.getRole());
            s.setContent(m.getContent());
            llmRequest.add(s);
        }

        String raw = aiSettingsService.sendLlmRequest(ai, llmRequest);

        // Save assistant response
        AiChatMessageEntity assistantMsg = new AiChatMessageEntity();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(raw != null ? raw.trim() : "");
        messageRepo.save(assistantMsg);

        return toMessageDto(assistantMsg);
    }

    // ── ESCALATE TO TICKET ────────────────────────────────────────────────────

    @Transactional
    public Long escalateToTicket(Long sessionId, Integer userId)
            throws IOException, URISyntaxException, InterruptedException {

        AiChatSessionEntity session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No AI configured");

        // Ask LLM to summarize into ticket
        List<AiChatMessageEntity> history = messageRepo.findBySessionIdOrderByCreatedAt(sessionId);
        String conversation = history.stream()
                .filter(m -> !"system".equals(m.getRole()))
                .map(m -> m.getRole().toUpperCase() + ": " + m.getContent())
                .collect(Collectors.joining("\n\n"));

        LlmStructure sys = new LlmStructure();
        sys.setRole("system");
        sys.setContent("You are a ticket creation assistant. Summarize the conversation into a support ticket. Return ONLY valid JSON, no markdown.");

        LlmStructure usr = new LlmStructure();
        usr.setRole("user");
        usr.setContent("Conversation:\n" + conversation + "\n\nReturn JSON: {\"title\": \"...\", \"description\": \"...\"}");

        String raw = aiSettingsService.sendLlmRequest(ai, List.of(sys, usr));
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> parsed;
        try {
            parsed = mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            parsed = new HashMap<>();
            parsed.put("title", session.getTitle() != null ? session.getTitle() : "Support Request");
            parsed.put("description", conversation);
        }

        String title = String.valueOf(parsed.getOrDefault("title", "Support Request"));
        String description = String.valueOf(parsed.getOrDefault("description", conversation));

        // Use ticketAiService to pick best template
        Long templateId = null;
        try {
            var suggestion = ticketAiService.analyzeAndSuggest(title + " " + description);
            templateId = suggestion.getMatchedTemplateId();
        } catch (Exception ignored) {}

        if (templateId == null) {
            templateId = ticketAiService.getDefaultTemplateId();
        }

        CreateTicketRequestDto dto = new CreateTicketRequestDto();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setStatus("new");
        dto.setTemplateId(templateId);
        dto.setTemplateVersionId(ticketAiService.getCurrentVersionId(templateId));
        dto.setRequestUserId(userId);
        dto.setLabelIds(Collections.emptyList());
        dto.setTicketData(new HashMap<>());

        TicketDetailDto created = ticketService.create(dto, userId);

        // Link session to new ticket
        session.setStatus("escalated");
        session.setTicketId(created.getId());
        sessionRepo.save(session);

        return created.getId();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private String buildSystemPrompt(AiChatSessionEntity session) {
        if ("ticket_consult".equals(session.getSessionType()) && session.getTicketId() != null) {
            return ticketRepo.findById(session.getTicketId())
                    .map(t -> "You are an expert IT support consultant reviewing ticket #" + t.getId()
                            + ": '" + t.getTitle() + "'. "
                            + "Description: " + (t.getDescription() != null ? t.getDescription() : "") + ". "
                            + "Help the support agent analyze, prioritize, and resolve this ticket.")
                    .orElse("You are an expert IT support consultant. Help the agent resolve the ticket.");
        }
        return "You are a helpful IT support assistant. Try to resolve the user's issue through conversation. "
                + "Be concise and practical. If you cannot solve the issue, acknowledge it clearly "
                + "and suggest the user create a support ticket.";
    }

    private AiChatSessionDto toSessionDto(AiChatSessionEntity s, List<AiChatMessageEntity> messages) {
        AiChatSessionDto dto = new AiChatSessionDto();
        dto.setId(s.getId());
        dto.setUserId(s.getUserId());
        dto.setTicketId(s.getTicketId());
        dto.setSessionType(s.getSessionType());
        dto.setStatus(s.getStatus());
        dto.setTitle(s.getTitle());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        dto.setMessages(messages.stream().map(this::toMessageDto).collect(Collectors.toList()));
        return dto;
    }

    private AiChatMessageDto toMessageDto(AiChatMessageEntity m) {
        AiChatMessageDto dto = new AiChatMessageDto();
        dto.setId(m.getId());
        dto.setSessionId(m.getSessionId());
        dto.setRole(m.getRole());
        dto.setContent(m.getContent());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
