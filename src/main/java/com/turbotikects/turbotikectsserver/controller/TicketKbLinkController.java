package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.KbArticleListDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.KbArticleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/kb-links")
@RequirePermission("AUTHENTICATED")
public class TicketKbLinkController {

    private final KbArticleService kbArticleService;

    public TicketKbLinkController(KbArticleService kbArticleService) {
        this.kbArticleService = kbArticleService;
    }

    @GetMapping
    public List<KbArticleListDto> getLinked(@PathVariable Long ticketId) {
        return kbArticleService.getLinkedArticles(ticketId);
    }

    @PostMapping("/{articleId}")
    public void link(@PathVariable Long ticketId, @PathVariable Long articleId) {
        kbArticleService.linkArticle(ticketId, articleId);
    }

    @DeleteMapping("/{articleId}")
    public void unlink(@PathVariable Long ticketId, @PathVariable Long articleId) {
        kbArticleService.unlinkArticle(ticketId, articleId);
    }
}
