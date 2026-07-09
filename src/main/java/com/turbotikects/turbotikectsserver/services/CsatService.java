package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.CsatSubmitRequestDto;
import com.turbotikects.turbotikectsserver.dto.CsatSurveyDto;
import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import com.turbotikects.turbotikectsserver.entitys.NotificationTemplateEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketCsatEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.NotificationTemplateRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketCsatRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class CsatService {

    private static final String TEMPLATE_TYPE = "ticket_csat_survey";

    private final TicketCsatRepository csatRepo;
    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;
    private final NotificationTemplateRepository templateRepo;
    private final EmailSenderService emailSenderService;
    private final UserNotificationService userNotificationService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public CsatService(TicketCsatRepository csatRepo, TicketRepository ticketRepo, UserRepository userRepo,
                        NotificationTemplateRepository templateRepo, EmailSenderService emailSenderService,
                        UserNotificationService userNotificationService) {
        this.csatRepo = csatRepo;
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
        this.templateRepo = templateRepo;
        this.emailSenderService = emailSenderService;
        this.userNotificationService = userNotificationService;
    }

    private static final java.util.Set<String> RESOLVED_STATUSES = java.util.Set.of("completed", "closed");

    /** Fire-and-forget: sends a CSAT survey the first time a ticket reaches a resolved status. */
    public void maybeSendSurvey(TicketEntity ticket, String previousStatus, String newStatus) {
        if (newStatus == null || newStatus.equals(previousStatus) || !RESOLVED_STATUSES.contains(newStatus)) return;
        if (csatRepo.existsByTicketId(ticket.getId())) return;

        TicketCsatEntity csat = new TicketCsatEntity();
        csat.setTicketId(ticket.getId());
        csat.setToken(UUID.randomUUID().toString());
        csat.setSentAt(LocalDateTime.now());
        csat.setExpiresAt(LocalDateTime.now().plusDays(7));
        csat = csatRepo.save(csat);

        UserEntity requester = userRepo.findById(ticket.getRequestUserId().longValue()).orElse(null);
        if (requester == null) {
            log.warn("[Csat] SKIPPED email/notification – requester {} not found for ticket {}",
                    ticket.getRequestUserId(), ticket.getId());
            return;
        }

        String surveyUrl = frontendUrl + "?csat=" + csat.getToken();
        sendSurveyEmail(ticket, requester, surveyUrl);

        userNotificationService.create(requester.getRed_id(), ticket.getId(), "CSAT_SURVEY",
                "How did we do? Share your feedback on TT-" + ticket.getId(), "?csat=" + csat.getToken());
    }

    private void sendSurveyEmail(TicketEntity ticket, UserEntity requester, String surveyUrl) {
        Optional<NotificationTemplateEntity> tmplOpt = templateRepo.findByNotificationType(TEMPLATE_TYPE);
        if (tmplOpt.isEmpty() || !tmplOpt.get().isEnabled()) {
            log.warn("[Csat] SKIPPED email – template '{}' missing or disabled", TEMPLATE_TYPE);
            return;
        }
        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[Csat] SKIPPED email – no default sender mailbox configured");
            return;
        }
        if (requester.getEmail() == null || requester.getEmail().isBlank()) {
            log.warn("[Csat] SKIPPED email – requester {} has no email address configured", requester.getRed_id());
            return;
        }

        UserEntity responsible = ticket.getResponsibleUserId() != null
                ? userRepo.findById(ticket.getResponsibleUserId().longValue()).orElse(null) : null;
        String responsibleName = responsible != null
                ? (responsible.getDisplayName() != null ? responsible.getDisplayName() : responsible.getUsername())
                : "our team";

        NotificationTemplateEntity tmpl = tmplOpt.get();
        String subject = resolvePlaceholders(tmpl.getSubjectTemplate(), ticket, requester, responsibleName, surveyUrl);
        String body    = resolvePlaceholders(tmpl.getBodyTemplate(), ticket, requester, responsibleName, surveyUrl);

        emailSenderService.sendReply(mailboxOpt.get(), requester.getEmail(), subject, body, ticket.getId());
    }

    private String resolvePlaceholders(String template, TicketEntity ticket, UserEntity requester,
                                        String responsibleName, String surveyUrl) {
        String requesterName = requester.getDisplayName() != null ? requester.getDisplayName() : requester.getUsername();
        return template
                .replace("{{ticket_id}}",        String.valueOf(ticket.getId()))
                .replace("{{ticket_title}}",     ticket.getTitle())
                .replace("{{requester_name}}",   requesterName)
                .replace("{{responsible_name}}", responsibleName)
                .replace("{{csat_survey_url}}",  surveyUrl);
    }

    public CsatSurveyDto getSurveyByToken(String token) {
        TicketCsatEntity csat = csatRepo.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found"));

        CsatSurveyDto dto = new CsatSurveyDto();
        dto.setTicketId(csat.getTicketId());
        dto.setExpired(csat.getExpiresAt().isBefore(LocalDateTime.now()));
        dto.setAlreadyResponded(csat.getRespondedAt() != null);
        dto.setExistingScore(csat.getScore());
        dto.setExistingComment(csat.getComment());

        // Ticket FK is ON DELETE CASCADE, so if we found the csat row the ticket still exists.
        TicketEntity ticket = ticketRepo.findById(csat.getTicketId()).orElse(null);
        if (ticket != null) {
            dto.setTicketTitle(ticket.getTitle());
            UserEntity responsible = ticket.getResponsibleUserId() != null
                    ? userRepo.findById(ticket.getResponsibleUserId().longValue()).orElse(null) : null;
            dto.setResolvedBy(responsible != null
                    ? (responsible.getDisplayName() != null ? responsible.getDisplayName() : responsible.getUsername())
                    : "our team");
            dto.setResolutionTime(formatDuration(Duration.between(ticket.getCreatedAt(), csat.getSentAt())));
        }
        return dto;
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours == 0) return minutes + "m";
        return hours + "h " + minutes + "m";
    }

    public void submitResponse(String token, CsatSubmitRequestDto dto) {
        TicketCsatEntity csat = csatRepo.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found"));
        if (csat.getRespondedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This survey has already been submitted");
        }
        if (csat.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This survey link has expired");
        }
        if (dto.getScore() == null || dto.getScore() < 1 || dto.getScore() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score must be between 1 and 5");
        }
        csat.setScore(dto.getScore());
        csat.setComment(dto.getComment());
        csat.setRespondedAt(LocalDateTime.now());
        csatRepo.save(csat);
    }
}
