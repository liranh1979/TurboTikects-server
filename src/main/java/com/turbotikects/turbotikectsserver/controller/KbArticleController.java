package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.KbAiService;
import com.turbotikects.turbotikectsserver.services.KbArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kb-articles")
@RequirePermission({"MANAGE_KNOWLEDGE_BASE", "TICKET_MANAGER"})
public class KbArticleController {

    private final KbArticleService kbArticleService;
    private final KbAiService kbAiService;

    public KbArticleController(KbArticleService kbArticleService, KbAiService kbAiService) {
        this.kbArticleService = kbArticleService;
        this.kbAiService = kbAiService;
    }

    @PostMapping("/generate-from-ticket/{ticketId}")
    public GenerateDraftResultDto generateFromTicket(@PathVariable Long ticketId) {
        return kbAiService.generateFromTicket(ticketId);
    }

    @PostMapping("/{id}/ai-review")
    public AiReviewResultDto aiReview(@PathVariable Long id) {
        return kbAiService.reviewArticle(id);
    }

    @GetMapping
    @RequirePermission("AUTHENTICATED")
    public List<KbArticleListDto> getAll(HttpServletRequest request) {
        return kbArticleService.getAll(canSeeInternal(request));
    }

    @GetMapping("/{id}")
    @RequirePermission("AUTHENTICATED")
    public KbArticleDetailDto getById(@PathVariable Long id, HttpServletRequest request) {
        return kbArticleService.getById(id, canSeeInternal(request));
    }

    @GetMapping("/search")
    @RequirePermission("AUTHENTICATED")
    public List<KbArticleListDto> search(@RequestParam String q, HttpServletRequest request) {
        return kbArticleService.search(q, canSeeInternal(request));
    }

    @GetMapping("/suggest")
    @RequirePermission("AUTHENTICATED")
    public List<KbSuggestResultDto> suggest(@RequestParam String q, HttpServletRequest request) {
        return kbArticleService.suggest(q, canSeeInternal(request));
    }

    @PostMapping("/{id}/feedback")
    @RequirePermission("AUTHENTICATED")
    public void feedback(@PathVariable Long id, @RequestParam boolean helpful) {
        kbArticleService.recordFeedback(id, helpful);
    }

    @PostMapping
    public KbArticleDetailDto create(@RequestBody SaveKbArticleRequestDto dto, HttpServletRequest request) {
        return kbArticleService.create(dto, currentUserId(request));
    }

    @PutMapping("/{id}")
    public KbArticleDetailDto update(@PathVariable Long id, @RequestBody SaveKbArticleRequestDto dto) {
        return kbArticleService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        kbArticleService.delete(id);
    }

    private boolean canSeeInternal(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto) {
            return dto.isSuperAdmin()
                    || (dto.getEffectivePermissions() != null
                        && (dto.getEffectivePermissions().contains("MANAGE_KNOWLEDGE_BASE")
                            || dto.getEffectivePermissions().contains("TICKET_MANAGER")));
        }
        return false;
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }
}
