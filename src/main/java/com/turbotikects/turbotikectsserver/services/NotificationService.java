package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.NotificationTemplateDto;
import com.turbotikects.turbotikectsserver.dto.UpdateNotificationTemplateDto;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationService {

    @Autowired private NotificationTemplateRepository templateRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private GroupMemberRepository groupMemberRepo;
    @Autowired private TicketActivityLogRepository activityRepo;
    @Autowired private EmailSenderService emailSenderService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    // ── Template management ──────────────────────────────────────────────────

    public List<NotificationTemplateDto> getAllTemplates() {
        return templateRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public NotificationTemplateDto updateTemplate(Long id, UpdateNotificationTemplateDto dto) {
        NotificationTemplateEntity tmpl = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        if (dto.getEnabled() != null) tmpl.setEnabled(dto.getEnabled());
        if (dto.getSubjectTemplate() != null && !dto.getSubjectTemplate().isBlank()) {
            tmpl.setSubjectTemplate(dto.getSubjectTemplate());
        }
        if (dto.getBodyTemplate() != null && !dto.getBodyTemplate().isBlank()) {
            tmpl.setBodyTemplate(dto.getBodyTemplate());
        }
        tmpl.setUpdatedAt(LocalDateTime.now());
        return toDto(templateRepo.save(tmpl));
    }

    @Transactional
    public NotificationTemplateDto rollbackToDefault(Long id) {
        NotificationTemplateEntity tmpl = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        tmpl.setSubjectTemplate(tmpl.getDefaultSubject());
        tmpl.setBodyTemplate(tmpl.getDefaultBody());
        tmpl.setUpdatedAt(LocalDateTime.now());
        return toDto(templateRepo.save(tmpl));
    }

    // ── Ticket lifecycle hooks ────────────────────────────────────────────────

    public void onTicketCreated(TicketEntity ticket, Integer actorId) {
        new Thread(() -> {
            try {
                if (ticket.getRequestUserId() != null) {
                    sendNotification("new_ticket_requester", ticket, ticket.getRequestUserId().longValue(), actorId);
                }

                if (ticket.getResponsibleUserId() != null) {
                    sendNotification("new_ticket_responsible", ticket, ticket.getResponsibleUserId().longValue(), actorId);
                } else if (ticket.getResponsibleGroupId() != null) {
                    notifyGroup("new_ticket_responsible", ticket, ticket.getResponsibleGroupId().longValue(), actorId);
                }
            } catch (Exception e) {
                log.error("Notification error on ticket created {}: {}", ticket.getId(), e.getMessage(), e);
            }
        }).start();
    }

    public void onTicketPatched(TicketEntity ticket, Integer actorId,
                                 Integer previousResponsibleUserId, Integer previousResponsibleGroupId) {
        new Thread(() -> {
            try {
                Integer requestUserId    = ticket.getRequestUserId();
                Integer responsibleUserId  = ticket.getResponsibleUserId();
                Integer responsibleGroupId = ticket.getResponsibleGroupId();

                // Notify requester unless they are the responsible admin who made the change
                // (always notify requester — they should know their ticket was updated)
                if (requestUserId != null
                        && !(requestUserId.equals(actorId) && requestUserId.equals(responsibleUserId))) {
                    sendNotification("ticket_updated_requester", ticket, requestUserId.longValue(), actorId);
                }

                // Notify responsible admin only if someone else made the change
                if (responsibleUserId != null && !responsibleUserId.equals(actorId)) {
                    sendNotification("ticket_updated_admin", ticket, responsibleUserId.longValue(), actorId);
                } else if (responsibleUserId == null && responsibleGroupId != null) {
                    notifyGroupExcluding("ticket_updated_admin", ticket, responsibleGroupId.longValue(), actorId);
                }

                // New responsibility assignment notification
                boolean responsibleUserChanged = !Objects.equals(responsibleUserId, previousResponsibleUserId);
                boolean responsibleGroupChanged = !Objects.equals(responsibleGroupId, previousResponsibleGroupId);

                if (responsibleUserChanged && responsibleUserId != null) {
                    sendNotification("new_ticket_responsible", ticket, responsibleUserId.longValue(), actorId);
                } else if (responsibleGroupChanged && responsibleGroupId != null
                        && !Objects.equals(responsibleGroupId, previousResponsibleGroupId)) {
                    notifyGroup("new_ticket_responsible", ticket, responsibleGroupId.longValue(), actorId);
                }
            } catch (Exception e) {
                log.error("Notification error on ticket patched {}: {}", ticket.getId(), e.getMessage(), e);
            }
        }).start();
    }

    // ── Diagnostic helper ─────────────────────────────────────────────────────

    public Map<String, Object> diagnose(Long ticketId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Check templates
        List<String> types = List.of("new_ticket_requester", "new_ticket_responsible",
                "ticket_updated_requester", "ticket_updated_admin");
        Map<String, String> templateStatus = new LinkedHashMap<>();
        for (String t : types) {
            Optional<NotificationTemplateEntity> opt = templateRepo.findByNotificationType(t);
            if (opt.isEmpty()) {
                templateStatus.put(t, "MISSING – run V48 migration or restart server");
            } else {
                templateStatus.put(t, opt.get().isEnabled() ? "enabled" : "disabled");
            }
        }
        result.put("templates", templateStatus);

        // Check default sender mailbox
        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            result.put("defaultMailbox", "MISSING – configure a mailbox and set it as default sender");
        } else {
            EmailMailboxEntity m = mailboxOpt.get();
            result.put("defaultMailbox", Map.of(
                    "id", m.getId(),
                    "displayName", m.getDisplayName(),
                    "email", m.getEmailAddress(),
                    "canSend", m.getCanSend()
            ));
        }

        // Check ticket and its users
        if (ticketId != null) {
            ticketRepo.findById(ticketId).ifPresentOrElse(ticket -> {
                result.put("ticketId", ticketId);
                result.put("requestUser", diagnoseUser(ticket.getRequestUserId()));
                result.put("responsibleUser", diagnoseUser(ticket.getResponsibleUserId()));
            }, () -> result.put("ticket", "NOT FOUND: " + ticketId));
        }

        return result;
    }

    private Map<String, Object> diagnoseUser(Integer userId) {
        if (userId == null) return Map.of("status", "not set");
        Optional<UserEntity> opt = userRepo.findById(userId.longValue());
        if (opt.isEmpty()) return Map.of("status", "USER NOT FOUND: " + userId);
        UserEntity u = opt.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getRed_id());
        m.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
        m.put("email", u.getEmail() != null ? u.getEmail() : "NO EMAIL – set email on this user account");
        return m;
    }

    @Autowired
    private com.turbotikects.turbotikectsserver.repositorys.TicketRepository ticketRepo;

    // ── Internal send helpers ─────────────────────────────────────────────────

    private void sendNotification(String type, TicketEntity ticket, Long recipientUserId, Integer actorId) {
        log.info("[Notification] type={} ticketId={} recipientUserId={}", type, ticket.getId(), recipientUserId);

        Optional<NotificationTemplateEntity> tmplOpt = templateRepo.findByNotificationType(type);
        if (tmplOpt.isEmpty()) {
            log.warn("[Notification] SKIPPED – template '{}' not found in DB (run V48 migration)", type);
            return;
        }
        if (!tmplOpt.get().isEnabled()) {
            log.info("[Notification] SKIPPED – template '{}' is disabled", type);
            return;
        }

        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[Notification] SKIPPED – no default sender mailbox configured (Settings → Email Integration)");
            return;
        }

        UserEntity recipient = userRepo.findById(recipientUserId).orElse(null);
        if (recipient == null) {
            log.warn("[Notification] SKIPPED – recipient user {} not found", recipientUserId);
            return;
        }
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.warn("[Notification] SKIPPED – recipient user {} ('{}') has no email address configured",
                    recipientUserId, recipient.getDisplayName() != null ? recipient.getDisplayName() : recipient.getUsername());
            return;
        }

        NotificationTemplateEntity tmpl = tmplOpt.get();
        String requesterName   = resolveDisplayName(ticket.getRequestUserId());
        String responsibleName = resolveDisplayName(ticket.getResponsibleUserId());

        String subject = resolvePlaceholders(tmpl.getSubjectTemplate(), ticket, requesterName, responsibleName);
        String body    = resolvePlaceholders(tmpl.getBodyTemplate(),    ticket, requesterName, responsibleName);

        log.info("[Notification] Sending '{}' to {} for ticket {}", type, recipient.getEmail(), ticket.getId());
        emailSenderService.sendReply(mailboxOpt.get(), recipient.getEmail(), subject, body, ticket.getId());

        try {
            writeOutboundActivity(ticket.getId(), actorId, recipient.getEmail(), subject, body);
        } catch (Exception ex) {
            log.warn("[Notification] Could not write activity log for ticket {} ({})", ticket.getId(), ex.getMessage());
        }
    }

    private void notifyGroup(String type, TicketEntity ticket, Long groupId, Integer actorId) {
        List<GroupMemberEntity> members = groupMemberRepo.findByGroupId(groupId);
        for (GroupMemberEntity m : members) {
            sendNotification(type, ticket, m.getUserId(), actorId);
        }
    }

    private void notifyGroupExcluding(String type, TicketEntity ticket, Long groupId, Integer actorId) {
        List<GroupMemberEntity> members = groupMemberRepo.findByGroupId(groupId);
        for (GroupMemberEntity m : members) {
            if (actorId != null && m.getUserId().equals(actorId.longValue())) continue;
            sendNotification(type, ticket, m.getUserId(), actorId);
        }
    }

    private String resolvePlaceholders(String template, TicketEntity ticket,
                                        String requesterName, String responsibleName) {
        String ticketUrl = frontendUrl + "?ticket=" + ticket.getId();
        String result = template
                .replace("{{ticket_id}}",          String.valueOf(ticket.getId()))
                .replace("{{ticket_title}}",       escapeHtml(ticket.getTitle()))
                .replace("{{ticket_status}}",      escapeHtml(ticket.getStatus()))
                .replace("{{ticket_description}}", ticket.getDescription() != null ? ticket.getDescription() : "")
                .replace("{{ticket_url}}",         ticketUrl)
                .replace("{{requester_name}}",     escapeHtml(requesterName))
                .replace("{{responsible_name}}",   escapeHtml(responsibleName));

        if (ticket.getTicketData() != null) {
            for (Map.Entry<String, Object> entry : ticket.getTicketData().entrySet()) {
                String val = entry.getValue() != null ? escapeHtml(entry.getValue().toString()) : "";
                result = result.replace("{{" + entry.getKey() + "}}", val);
            }
        }
        return result;
    }

    private String resolveDisplayName(Integer userId) {
        if (userId == null) return "";
        return userRepo.findById(userId.longValue())
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Unknown");
    }

    private void writeOutboundActivity(Long ticketId, Integer actorId, String to, String subject, String body) {
        TicketActivityLogEntity entry = new TicketActivityLogEntity();
        entry.setTicketId(ticketId);
        entry.setActorId(actorId != null ? actorId : 1);
        entry.setOperation("NOTIFICATION_SENT");
        entry.setActivityType("email_outbound");
        LinkedHashMap<String, Object> changes = new LinkedHashMap<>();
        changes.put("to", to);
        changes.put("subject", subject);
        changes.put("body", body);
        entry.setChanges(changes);
        activityRepo.save(entry);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private NotificationTemplateDto toDto(NotificationTemplateEntity e) {
        NotificationTemplateDto dto = new NotificationTemplateDto();
        dto.setId(e.getId());
        dto.setNotificationType(e.getNotificationType());
        dto.setDisplayName(e.getDisplayName());
        dto.setDescription(e.getDescription());
        dto.setEnabled(e.isEnabled());
        dto.setSubjectTemplate(e.getSubjectTemplate());
        dto.setBodyTemplate(e.getBodyTemplate());
        dto.setAdminFacing(e.isAdminFacing());
        return dto;
    }
}
