package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.TicketSseEventDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EmailProcessorService {

    private static final Pattern TICKET_REF = Pattern.compile("\\[TT-(\\d+)\\]");

    @Autowired private EmailMessageLogRepository messageLogRepo;
    @Autowired private EmailFilterRepository filterRepo;
    @Autowired private TicketRepository ticketRepo;
    @Autowired private TicketActivityLogRepository activityRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private TemplateRepository templateRepo;
    @Autowired private TemplateVersionRepository templateVersionRepo;
    @Autowired private EmailMailboxRepository mailboxRepo;
    @Autowired private EmailSenderService emailSenderService;
    @Autowired private SignatureStripperService signatureStripper;
    @Autowired private AiSettingsService aiSettingsService;
    @Autowired private AttachmentService attachmentService;
    @Autowired private TicketSseService ticketSseService;

    // System user ID used for automated actions (root user's red_id)
    private static final int SYSTEM_ACTOR_ID = 1;

    @Transactional
    public void process(EmailMailboxEntity mailbox, InboundEmail email) {
        // 1. Dedup check
        if (email.getMessageId() != null
                && messageLogRepo.findByMessageId(email.getMessageId()).isPresent()) {
            log.debug("Skipping duplicate message: {}", email.getMessageId());
            return;
        }

        // 2. Filter check
        if (isFiltered(mailbox, email.getFromAddress())) {
            log.info("Email from {} filtered for mailbox {}", email.getFromAddress(), mailbox.getId());
            logMessage(mailbox.getId(), email.getMessageId(), null);
            return;
        }

        // 3. Strip signature
        String body = email.getHtmlBody() != null ? email.getHtmlBody() : "<p>" + email.getTextBody() + "</p>";
        if (Boolean.TRUE.equals(mailbox.getIgnoreSignature())) {
            body = signatureStripper.stripFromHtml(body);
        }

        // 4. Look for [TT-NNN] in subject
        Long referencedTicketId = extractTicketId(email.getSubject());

        if (referencedTicketId != null) {
            // ── REPLY PATH ──────────────────────────────────────────────────────
            Optional<TicketEntity> ticketOpt = ticketRepo.findById(referencedTicketId);
            if (ticketOpt.isPresent()) {
                handleReply(mailbox, email, ticketOpt.get(), body);
                return;
            }
        }

        // ── NEW TICKET PATH ─────────────────────────────────────────────────────
        handleNewTicket(mailbox, email, body);
    }

    private void handleReply(EmailMailboxEntity mailbox, InboundEmail email,
                              TicketEntity ticket, String body) {
        // Replace inline CID references with permanent attachment URLs
        if (!email.getInlineAttachments().isEmpty()) {
            body = processCidImages(body, email.getInlineAttachments(), ticket.getId());
        }

        // Save activity
        writeActivity(ticket.getId(), SYSTEM_ACTOR_ID, "EMAIL_REPLY", "email_inbound",
                Map.of("from", email.getFromAddress(), "subject", email.getSubject(), "body", body));

        // Reopen if closed
        if ("closed".equalsIgnoreCase(ticket.getStatus()) || "resolved".equalsIgnoreCase(ticket.getStatus())) {
            ticket.setStatus("reopened");
            ticket.setUpdatedAt(LocalDateTime.now());
            ticket.setVersion(ticket.getVersion() + 1);
            ticketRepo.save(ticket);
        }

        // Notify connected clients of ticket update
        TicketSseEventDto updatedEvent = new TicketSseEventDto();
        updatedEvent.setType("TICKET_UPDATED");
        updatedEvent.setTicketId(ticket.getId());
        updatedEvent.setNewVersion(ticket.getVersion());
        updatedEvent.setOperation("EMAIL_REPLY");
        ticketSseService.publish(updatedEvent);

        // Log message
        logMessage(mailbox.getId(), email.getMessageId(), ticket.getId());

        // AI triage on reply (optional solution suggestion)
        if (Boolean.TRUE.equals(mailbox.getAiEnabled())) {
            tryAiSolution(mailbox, ticket, email.getSubject(), body);
        }

        log.info("Processed reply for ticket {} from {}", ticket.getId(), email.getFromAddress());
    }

    private void handleNewTicket(EmailMailboxEntity mailbox, InboundEmail email, String body) {
        // Resolve or create user
        UserEntity requestUser = resolveUser(mailbox, email.getFromAddress(), email.getFromName());
        if (requestUser == null) {
            log.info("Ignoring email from unknown sender: {}", email.getFromAddress());
            logMessage(mailbox.getId(), email.getMessageId(), null);
            return;
        }

        // Find default template
        TemplateEntity template = templateRepo.findByIsDefaultTrue()
                .orElseGet(() -> templateRepo.findAll().stream().findFirst().orElse(null));
        if (template == null) {
            log.error("No template found — cannot create ticket from email");
            logMessage(mailbox.getId(), email.getMessageId(), null);
            return;
        }

        TemplateVersionEntity version = templateVersionRepo
                .findByTemplateIdAndIsCurrentTrue(template.getId())
                .orElseGet(() -> templateVersionRepo
                        .findByTemplateIdOrderByVersionNumberDesc(template.getId())
                        .stream().findFirst().orElse(null));
        if (version == null) {
            log.error("No template version found for template {}", template.getId());
            logMessage(mailbox.getId(), email.getMessageId(), null);
            return;
        }

        // Create ticket
        TicketEntity ticket = new TicketEntity();
        ticket.setTitle(email.getSubject() != null ? email.getSubject() : "(no subject)");
        ticket.setDescription(body);
        ticket.setStatus("new");
        ticket.setTemplateId(template.getId());
        ticket.setTemplateVersionId(version.getId());
        ticket.setRequestUserId(requestUser.getRed_id().intValue());
        ticket.setVersion(1);
        ticket.setSourceType("email");
        ticket = ticketRepo.save(ticket);

        // Replace inline CID references with permanent attachment URLs
        if (!email.getInlineAttachments().isEmpty()) {
            String processedBody = processCidImages(body, email.getInlineAttachments(), ticket.getId());
            ticket.setDescription(processedBody);
            ticketRepo.save(ticket);
        }

        // Notify connected clients of new ticket
        TicketSseEventDto createdEvent = new TicketSseEventDto();
        createdEvent.setType("TICKET_UPDATED");
        createdEvent.setTicketId(ticket.getId());
        createdEvent.setOperation("TICKET_CREATED");
        ticketSseService.publish(createdEvent);

        // Activity: email inbound
        writeActivity(ticket.getId(), SYSTEM_ACTOR_ID, "TICKET_CREATED", "email_inbound",
                Map.of("from", email.getFromAddress(), "subject", email.getSubject() != null ? email.getSubject() : ""));

        // Log message
        logMessage(mailbox.getId(), email.getMessageId(), ticket.getId());

        // Send confirmation reply
        if (Boolean.TRUE.equals(mailbox.getCanSend())) {
            String ticketRef = "[TT-" + ticket.getId() + "]";
            String confirmSubject = "Re: " + ticketRef + " " + ticket.getTitle();
            String confirmBody = "<p>Your request has been received and ticket <strong>" + ticketRef + "</strong> has been created.</p>"
                    + "<p>You can reply to this email to add updates to your ticket.</p>";
            emailSenderService.sendReply(mailbox, email.getFromAddress(), confirmSubject, confirmBody, ticket.getId());
        }

        // AI triage
        if (Boolean.TRUE.equals(mailbox.getAiEnabled())) {
            tryAiTriage(mailbox, ticket, email.getSubject(), body);
        }

        log.info("Created ticket {} from email by {}", ticket.getId(), email.getFromAddress());
    }

    private void tryAiTriage(EmailMailboxEntity mailbox, TicketEntity ticket,
                              String subject, String body) {
        try {
            AiSettingsEntity ai = aiSettingsService.getActiveAi();
            if (ai == null) return;

            String plainBody = Jsoup.parse(body).text();
            String prompt = "Ticket title: " + subject + "\nBody: " + plainBody + "\n"
                    + "Return JSON only: {\"solution\": \"...\", \"confidence\": 0-100, \"reasoning\": \"...\"}";

            List<LlmStructure> messages = List.of(
                    new LlmStructure("system", "You are a helpdesk triage assistant. Return JSON only."),
                    new LlmStructure("user", prompt)
            );

            String responseText = aiSettingsService.sendLlmRequest(ai, messages);
            parseAndApplyAiResponse(mailbox, ticket, responseText);
        } catch (Exception e) {
            log.error("AI triage failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    private void tryAiSolution(EmailMailboxEntity mailbox, TicketEntity ticket,
                                String subject, String body) {
        try {
            AiSettingsEntity ai = aiSettingsService.getActiveAi();
            if (ai == null) return;

            String plainBody = Jsoup.parse(body).text();
            String prompt = "A customer replied to ticket [TT-" + ticket.getId() + "]: " + subject
                    + "\nReply content: " + plainBody + "\n"
                    + "Return JSON only: {\"solution\": \"...\", \"confidence\": 0-100}";

            List<LlmStructure> messages = List.of(
                    new LlmStructure("system", "You are a helpdesk assistant. Return JSON only."),
                    new LlmStructure("user", prompt)
            );

            String responseText = aiSettingsService.sendLlmRequest(ai, messages);
            parseAndApplyAiResponse(mailbox, ticket, responseText);
        } catch (Exception e) {
            log.error("AI solution failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    private void parseAndApplyAiResponse(EmailMailboxEntity mailbox, TicketEntity ticket,
                                          String responseText) {
        try {
            // Extract JSON from response (may be wrapped in markdown)
            String json = responseText.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new ObjectMapper().readValue(json, Map.class);
            String solution = (String) parsed.get("solution");
            Object confObj = parsed.get("confidence");
            int confidence = confObj instanceof Number ? ((Number) confObj).intValue() : 0;

            if (confidence >= mailbox.getAiConfidenceThreshold() && solution != null && !solution.isBlank()) {
                writeActivity(ticket.getId(), SYSTEM_ACTOR_ID, "AI_SOLUTION", "ai_solution",
                        Map.of("solution", solution, "confidence", confidence));

                if (Boolean.TRUE.equals(mailbox.getCanSend()) && ticket.getRequestUserId() != null) {
                    String ticketRef = "[TT-" + ticket.getId() + "]";
                    String replySubject = "Re: " + ticketRef + " " + ticket.getTitle();
                    String replyBody = "<p><strong>AI Response:</strong></p><p>" + solution + "</p>"
                            + "<p><em>Confidence: " + confidence + "%</em></p>";
                    Optional<UserEntity> reqUser = userRepo.findById(ticket.getRequestUserId().longValue());
                    reqUser.ifPresent(u -> {
                        if (u.getEmail() != null) {
                            emailSenderService.sendReply(mailbox, u.getEmail(), replySubject, replyBody, ticket.getId());
                        }
                    });
                }
            } else {
                writeActivity(ticket.getId(), SYSTEM_ACTOR_ID, "AI_ANALYSIS", "ai",
                        Map.of("message", "AI could not provide a confident solution", "confidence", confidence));
            }
        } catch (Exception e) {
            log.error("Failed to parse AI response for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────────

    private String processCidImages(String html, Map<String, InboundEmail.InlineAttachment> inlines, Long ticketId) {
        String result = html;
        for (Map.Entry<String, InboundEmail.InlineAttachment> entry : inlines.entrySet()) {
            try {
                com.turbotikects.turbotikectsserver.dto.AttachmentDto saved = attachmentService.uploadBytes(
                        "ticket", ticketId,
                        entry.getValue().data(), entry.getValue().mimeType(), entry.getValue().filename(),
                        SYSTEM_ACTOR_ID);
                result = result.replace("cid:" + entry.getKey(),
                        "/api/v1/attachments/" + saved.getId() + "/inline");
            } catch (Exception e) {
                log.error("Failed to save inline attachment cid:{}: {}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    private Long extractTicketId(String subject) {
        if (subject == null) return null;
        Matcher m = TICKET_REF.matcher(subject);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean isFiltered(EmailMailboxEntity mailbox, String fromAddress) {
        if (fromAddress == null) return false;

        List<EmailFilterEntity> filters = new ArrayList<>(filterRepo.findByMailboxId(mailbox.getId()));
        filters.addAll(filterRepo.findByMailboxIdIsNull());

        List<EmailFilterEntity> whitelist = filters.stream()
                .filter(f -> "whitelist".equals(f.getListType())).toList();
        List<EmailFilterEntity> blacklist = filters.stream()
                .filter(f -> "blacklist".equals(f.getListType())).toList();

        // Blacklist check
        for (EmailFilterEntity f : blacklist) {
            if (matchesPattern(fromAddress, f.getEmailPattern())) return true;
        }

        // Whitelist check (if whitelist is non-empty, must be in it)
        if (!whitelist.isEmpty()) {
            return whitelist.stream().noneMatch(f -> matchesPattern(fromAddress, f.getEmailPattern()));
        }

        return false;
    }

    private boolean matchesPattern(String email, String pattern) {
        if (pattern == null) return false;
        if (!pattern.contains("*")) return pattern.equalsIgnoreCase(email);
        // Wildcard matching: *@domain.com
        String regex = Pattern.quote(pattern).replace("\\*", ".*");
        return email.toLowerCase().matches(regex.toLowerCase());
    }

    private UserEntity resolveUser(EmailMailboxEntity mailbox, String fromAddress, String fromName) {
        Optional<UserEntity> existing = userRepo.findByEmail(fromAddress);
        if (existing.isPresent()) return existing.get();

        if ("create_user".equals(mailbox.getUnknownSenderAction())) {
            int atIdx = fromAddress.indexOf('@');
            String localPart = atIdx > 0 ? fromAddress.substring(0, atIdx) : fromAddress;
            UserEntity newUser = new UserEntity();
            newUser.setUsername(fromAddress);
            newUser.setEmail(fromAddress);
            newUser.setDisplayName(localPart);
            newUser.setPassword("");
            newUser.setSuperAdmin(false);
            newUser.setSourceType(3); // email-created
            return userRepo.save(newUser);
        }

        return null;
    }

    private void writeActivity(Long ticketId, Integer actorId, String operation,
                                String activityType, Map<String, Object> changes) {
        TicketActivityLogEntity entry = new TicketActivityLogEntity();
        entry.setTicketId(ticketId);
        entry.setActorId(actorId);
        entry.setOperation(operation);
        entry.setActivityType(activityType);
        entry.setChanges(changes);
        activityRepo.save(entry);
    }

    private void logMessage(Long mailboxId, String messageId, Long ticketId) {
        EmailMessageLogEntity log = new EmailMessageLogEntity();
        log.setMailboxId(mailboxId);
        log.setMessageId(messageId != null ? messageId : "no-id-" + System.currentTimeMillis());
        log.setTicketId(ticketId);
        log.setDirection("inbound");
        messageLogRepo.save(log);
    }

    // ── Inbound email data container ──────────────────────────────────────────────

    public static class InboundEmail {
        public record InlineAttachment(byte[] data, String mimeType, String filename) {}

        private String messageId;
        private String fromAddress;
        private String fromName;
        private String subject;
        private String htmlBody;
        private String textBody;
        private final Map<String, InlineAttachment> inlineAttachments = new HashMap<>();

        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getFromAddress() { return fromAddress; }
        public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getHtmlBody() { return htmlBody; }
        public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
        public String getTextBody() { return textBody; }
        public void setTextBody(String textBody) { this.textBody = textBody; }
        public Map<String, InlineAttachment> getInlineAttachments() { return inlineAttachments; }
        public void addInlineAttachment(String cid, InlineAttachment att) { inlineAttachments.put(cid, att); }
    }
}
