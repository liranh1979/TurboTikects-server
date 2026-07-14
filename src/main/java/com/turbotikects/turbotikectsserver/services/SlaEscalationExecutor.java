package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.GroupMemberRepository;
import com.turbotikects.turbotikectsserver.repositorys.NotificationTemplateRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes one fired {@link SlaEscalationStepEntity} against a ticket: sends an email/in-app
 * notification, reassigns the ticket, or posts to a webhook. Reuses the existing
 * notification_templates + EmailSenderService/UserNotificationService pipeline exactly as-is
 * (same as CSAT) rather than building a parallel one — see plan decision #7.
 */
@Slf4j
@Service
public class SlaEscalationExecutor {

    private final UserRepository userRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final TicketRepository ticketRepo;
    private final NotificationTemplateRepository templateRepo;
    private final EmailSenderService emailSenderService;
    private final UserNotificationService userNotificationService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlaEscalationExecutor(UserRepository userRepo, GroupMemberRepository groupMemberRepo,
                                  TicketRepository ticketRepo, NotificationTemplateRepository templateRepo,
                                  EmailSenderService emailSenderService, UserNotificationService userNotificationService) {
        this.userRepo = userRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.ticketRepo = ticketRepo;
        this.templateRepo = templateRepo;
        this.emailSenderService = emailSenderService;
        this.userNotificationService = userNotificationService;
    }

    public void execute(TicketEntity ticket, SlaEscalationStepEntity step, boolean isBreach, double percentUsed) {
        switch (step.getActionType()) {
            case "NOTIFY_EMAIL" -> sendEmail(ticket, step, isBreach, percentUsed);
            case "NOTIFY_INAPP" -> sendInApp(ticket, step, isBreach);
            case "REASSIGN" -> reassign(ticket, step);
            case "WEBHOOK" -> sendWebhook(ticket, step, isBreach, percentUsed);
            default -> log.warn("[SLA] Unknown escalation action_type '{}' on step {}", step.getActionType(), step.getId());
        }
    }

    private List<Integer> resolveTargetUserIds(TicketEntity ticket, SlaEscalationStepEntity step) {
        if (step.getTargetUserId() != null) return List.of(step.getTargetUserId());
        if (step.getTargetGroupId() != null) {
            List<Integer> members = groupMemberRepo.findByGroupId(step.getTargetGroupId().longValue())
                    .stream().map(m -> m.getUserId().intValue()).toList();
            if (!members.isEmpty()) return members;
        }
        // No explicit target configured — fall back to the ticket's currently assigned agent.
        return ticket.getResponsibleUserId() != null ? List.of(ticket.getResponsibleUserId()) : List.of();
    }

    private void sendEmail(TicketEntity ticket, SlaEscalationStepEntity step, boolean isBreach, double percentUsed) {
        String templateType = "REASSIGN".equals(step.getActionType()) ? "sla_escalation" : isBreach ? "sla_breach" : "sla_warning";
        Optional<NotificationTemplateEntity> tmplOpt = templateRepo.findByNotificationType(templateType);
        if (tmplOpt.isEmpty() || !tmplOpt.get().isEnabled()) {
            log.warn("[SLA] SKIPPED email — template '{}' missing or disabled", templateType);
            return;
        }
        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[SLA] SKIPPED email — no default sender mailbox configured");
            return;
        }
        NotificationTemplateEntity tmpl = tmplOpt.get();
        String subject = resolvePlaceholders(tmpl.getSubjectTemplate(), ticket, percentUsed);
        String body = resolvePlaceholders(tmpl.getBodyTemplate(), ticket, percentUsed);

        for (Integer userId : resolveTargetUserIds(ticket, step)) {
            UserEntity user = userRepo.findById(userId.longValue()).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
            emailSenderService.sendReply(mailboxOpt.get(), user.getEmail(), subject, body, ticket.getId());
        }
    }

    private void sendInApp(TicketEntity ticket, SlaEscalationStepEntity step, boolean isBreach) {
        String message = (isBreach ? "SLA breached on " : "SLA warning on ") + "TT-" + ticket.getId() + " — " + ticket.getTitle();
        for (Integer userId : resolveTargetUserIds(ticket, step)) {
            userNotificationService.create(userId.longValue(), ticket.getId(), isBreach ? "SLA_BREACH" : "SLA_WARNING",
                    message, "/tickets/" + ticket.getId());
        }
    }

    private void reassign(TicketEntity ticket, SlaEscalationStepEntity step) {
        if (step.getTargetUserId() == null && step.getTargetGroupId() == null) {
            log.warn("[SLA] REASSIGN step {} has no target_user_id/target_group_id — skipped", step.getId());
            return;
        }
        if (step.getTargetUserId() != null) ticket.setResponsibleUserId(step.getTargetUserId());
        if (step.getTargetGroupId() != null) ticket.setResponsibleGroupId(step.getTargetGroupId());
        ticketRepo.save(ticket);
        sendEmail(ticket, step, false, 0); // "you've been assigned via SLA escalation" — sla_escalation template
        sendInApp(ticket, step, false);
    }

    private void sendWebhook(TicketEntity ticket, SlaEscalationStepEntity step, boolean isBreach, double percentUsed) {
        if (step.getWebhookUrl() == null || step.getWebhookUrl().isBlank()) {
            log.warn("[SLA] WEBHOOK step {} has no webhook_url — skipped", step.getId());
            return;
        }
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "ticketId", ticket.getId(),
                    "ticketTitle", ticket.getTitle(),
                    "priority", String.valueOf(ticket.getPriority()),
                    "isBreach", isBreach,
                    "percentUsed", percentUsed
            ));
            HttpRequest req = HttpRequest.newBuilder(new URI(step.getWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                log.error("[SLA] Webhook POST to {} failed ({}): {}", step.getWebhookUrl(), res.statusCode(), res.body());
            }
        } catch (Exception e) {
            log.error("[SLA] Webhook POST to {} threw", step.getWebhookUrl(), e);
        }
    }

    private String resolvePlaceholders(String template, TicketEntity ticket, double percentUsed) {
        if (template == null) return "";
        return template
                .replace("{{ticket_id}}", String.valueOf(ticket.getId()))
                .replace("{{ticket_title}}", ticket.getTitle() != null ? ticket.getTitle() : "")
                .replace("{{priority}}", ticket.getPriority() != null ? ticket.getPriority() : "")
                .replace("{{percent_used}}", String.valueOf(Math.round(percentUsed)));
    }
}
