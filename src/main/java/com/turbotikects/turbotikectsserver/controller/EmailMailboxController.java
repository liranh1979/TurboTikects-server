package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.EmailConnectionTestResultDto;
import com.turbotikects.turbotikectsserver.dto.EmailFilterDto;
import com.turbotikects.turbotikectsserver.dto.EmailMailboxDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.EmailMailboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/email")
@RequirePermission("MANAGE_EMAIL")
public class EmailMailboxController {

    @Autowired
    private EmailMailboxService emailMailboxService;

    // ── Mailbox CRUD ─────────────────────────────────────────────────────────────

    @GetMapping("/mailboxes")
    public List<EmailMailboxDto> getAllMailboxes() {
        return emailMailboxService.getAllMailboxes();
    }

    @PostMapping("/mailboxes")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailMailboxDto createMailbox(@RequestBody EmailMailboxDto dto) {
        return emailMailboxService.createMailbox(dto);
    }

    @PatchMapping("/mailboxes/{id}")
    public EmailMailboxDto updateMailbox(@PathVariable Long id, @RequestBody EmailMailboxDto dto) {
        return emailMailboxService.updateMailbox(id, dto);
    }

    @DeleteMapping("/mailboxes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMailbox(@PathVariable Long id) {
        emailMailboxService.deleteMailbox(id);
    }

    @PostMapping("/mailboxes/{id}/test")
    public EmailConnectionTestResultDto testConnection(@PathVariable Long id) {
        return emailMailboxService.testConnection(id);
    }

    @PostMapping("/mailboxes/{id}/set-default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefault(@PathVariable Long id) {
        emailMailboxService.setDefaultSender(id);
    }

    // ── Filters ───────────────────────────────────────────────────────────────────

    @GetMapping("/mailboxes/{id}/filters")
    public List<EmailFilterDto> getFilters(@PathVariable Long id) {
        return emailMailboxService.getFilters(id);
    }

    @PostMapping("/mailboxes/{id}/filters")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailFilterDto addFilter(@PathVariable Long id, @RequestBody EmailFilterDto dto) {
        return emailMailboxService.addFilter(id, dto);
    }

    @DeleteMapping("/mailboxes/{id}/filters/{filterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFilter(@PathVariable Long id, @PathVariable Long filterId) {
        emailMailboxService.deleteFilter(id, filterId);
    }

    // ── OAuth2 ───────────────────────────────────────────────────────────────────

    @GetMapping("/mailboxes/{id}/oauth2/authorize")
    public Map<String, String> getAuthUrl(@PathVariable Long id, @RequestParam String provider) {
        String url = "gmail".equals(provider)
                ? emailMailboxService.buildGmailAuthUrl(id)
                : emailMailboxService.buildMicrosoftAuthUrl(id);
        return Map.of("authUrl", url);
    }

    @GetMapping("/oauth2/{provider}/callback")
    public String oauthCallback(@PathVariable String provider,
                                 @RequestParam String code,
                                 @RequestParam String state) throws Exception {
        Long mailboxId = Long.parseLong(state);
        emailMailboxService.handleOAuth2Callback(mailboxId, code, provider);
        return "<html><body><script>window.close();</script><p>Authorized. You can close this window.</p></body></html>";
    }
}
