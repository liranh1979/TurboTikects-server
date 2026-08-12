package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiReviewResultDto;
import com.turbotikects.turbotikectsserver.dto.GenerateDraftResultDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.KbArticleEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketActivityLogEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketLabelAssignmentEntity;
import com.turbotikects.turbotikectsserver.repositorys.KbArticleRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketActivityLogRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketLabelAssignmentRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KbAiService {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CHANGE_DESC_LENGTH = 300;
    private static final int MAX_CHANGES_RETURNED = 20;

    private final AiSettingsService aiSettingsService;
    private final TicketRepository ticketRepo;
    private final TicketActivityLogRepository activityRepo;
    private final KbArticleRepository articleRepo;
    private final TicketLabelAssignmentRepository ticketLabelAssignmentRepo;

    public KbAiService(AiSettingsService aiSettingsService, TicketRepository ticketRepo,
                        TicketActivityLogRepository activityRepo, KbArticleRepository articleRepo,
                        TicketLabelAssignmentRepository ticketLabelAssignmentRepo) {
        this.aiSettingsService = aiSettingsService;
        this.ticketRepo = ticketRepo;
        this.activityRepo = activityRepo;
        this.articleRepo = articleRepo;
        this.ticketLabelAssignmentRepo = ticketLabelAssignmentRepo;
    }

    public GenerateDraftResultDto generateFromTicket(Long ticketId) {
        AiSettingsEntity ai = requireActiveAi();

        TicketEntity ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        String resolutionText = activityRepo.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .filter(a -> "ai_solution".equals(a.getActivityType()))
                .map(this::extractSolutionText)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You are drafting a Knowledge Base article from a resolved IT support ticket. Given the \
                ticket title, description, and resolution notes, write a clear, reusable self-service \
                KB article that would help future users solve the same problem.

                Respond with EXACTLY this JSON shape (no markdown, no explanation, ONLY the JSON):
                {"title": "short article title", "body": "<p>simple HTML paragraphs</p>"}

                body must be valid simple HTML using ONLY p, ul, li, strong, em, code, h3 tags. Do not \
                invent facts not present in the ticket content. If no resolution text is available, \
                write general troubleshooting guidance based on the title/description alone.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Ticket title: " + nullToEmpty(ticket.getTitle())
                + "\n\nTicket description: " + nullToEmpty(ticket.getDescription())
                + "\n\nResolution notes: " + resolutionText);

        String raw = callLlm(ai, system, user);
        Map<String, Object> parsed = parseJsonObject(raw);

        GenerateDraftResultDto result = new GenerateDraftResultDto();
        result.setTitle(clamp(String.valueOf(parsed.getOrDefault("title", "Untitled Article")), MAX_TITLE_LENGTH));
        result.setBody(sanitizeHtml(String.valueOf(parsed.getOrDefault("body", ""))));
        // Tags are inherited from the ticket's real labels, not invented by the AI — the same
        // tag vocabulary tickets use, per explicit design decision (not free-text AI guesses).
        result.setLabelIds(ticketLabelAssignmentRepo.findByTicketId(ticketId).stream()
                .map(TicketLabelAssignmentEntity::getLabelId)
                .collect(java.util.stream.Collectors.toList()));
        return result;
    }

    public AiReviewResultDto reviewArticle(Long articleId) {
        AiSettingsEntity ai = requireActiveAi();

        KbArticleEntity article = articleRepo.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("""
                You are reviewing an internal IT Knowledge Base article for accuracy, clarity, and \
                completeness. Given the current title and body (HTML), identify factual mistakes, \
                outdated instructions, unclear wording, or gaps, and propose an improved, expanded \
                version.

                Respond with EXACTLY this JSON shape (no markdown, no explanation, ONLY the JSON):
                {"proposedBody": "<p>simple HTML paragraphs</p>", "changes": [{"type":"fix|expansion|clarity","description":"short human-readable summary"}]}

                proposedBody must be valid simple HTML using ONLY p, ul, li, strong, em, code, h3 tags. \
                Preserve the article's core meaning — do not invent new unrelated content. If the \
                article is already accurate and complete, return the same body unchanged with an empty \
                changes array.
                """);

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Title: " + nullToEmpty(article.getTitle()) + "\n\nBody:\n" + nullToEmpty(article.getBody()));

        String raw = callLlm(ai, system, user);
        Map<String, Object> parsed = parseJsonObject(raw);

        AiReviewResultDto result = new AiReviewResultDto();
        result.setProposedBody(sanitizeHtml(String.valueOf(parsed.getOrDefault("proposedBody", article.getBody()))));
        result.setChanges(sanitizeChanges(parsed.get("changes")));
        return result;
    }

    private AiSettingsEntity requireActiveAi() {
        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active AI provider configured");
        }
        return ai;
    }

    private String callLlm(AiSettingsEntity ai, LlmStructure system, LlmStructure user) {
        try {
            return aiSettingsService.sendLlmRequest(ai, List.of(system, user));
        } catch (Exception e) {
            log.error("[KB AI] LLM call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request failed: " + e.getMessage());
        }
    }

    private Map<String, Object> parseJsonObject(String raw) {
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
        try {
            return new ObjectMapper().readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[KB AI] Could not parse AI response: {}", raw, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned an unreadable response");
        }
    }

    /**
     * The two existing activity-log write sites for AI solutions use inconsistent keys —
     * TicketService.saveAiSolution writes "body", EmailProcessorService's auto-reply path writes
     * "solution" — so both must be checked here.
     */
    private String extractSolutionText(TicketActivityLogEntity activity) {
        Map<String, Object> changes = activity.getChanges();
        if (changes == null) return null;
        Object body = changes.get("body");
        if (body != null) return String.valueOf(body);
        Object solution = changes.get("solution");
        return solution != null ? String.valueOf(solution) : null;
    }

    /** Defense in depth — never trust AI-returned HTML, especially on the generate-from-ticket path
     *  where ticket content (attacker-influenceable, e.g. an end user's own ticket description)
     *  flows into the prompt. */
    private String sanitizeHtml(String html) {
        return Jsoup.clean(html, Safelist.relaxed());
    }

    @SuppressWarnings("unchecked")
    private List<AiReviewResultDto.AiReviewChangeDto> sanitizeChanges(Object raw) {
        List<AiReviewResultDto.AiReviewChangeDto> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return result;
        for (Object o : list) {
            if (result.size() >= MAX_CHANGES_RETURNED) break;
            if (!(o instanceof Map<?, ?> m)) continue;
            String type = String.valueOf(m.get("type"));
            if (!type.equals("fix") && !type.equals("expansion") && !type.equals("clarity")) type = "fix";
            String description = m.get("description") != null ? clamp(String.valueOf(m.get("description")), MAX_CHANGE_DESC_LENGTH) : "";
            AiReviewResultDto.AiReviewChangeDto dto = new AiReviewResultDto.AiReviewChangeDto();
            dto.setType(type);
            dto.setDescription(description);
            result.add(dto);
        }
        return result;
    }

    private String clamp(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
