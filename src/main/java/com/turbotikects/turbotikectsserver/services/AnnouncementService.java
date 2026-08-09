package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.AnnouncementDto;
import com.turbotikects.turbotikectsserver.dto.PublicAnnouncementDto;
import com.turbotikects.turbotikectsserver.dto.SaveAnnouncementRequestDto;
import com.turbotikects.turbotikectsserver.entitys.AlertTypeEntity;
import com.turbotikects.turbotikectsserver.entitys.AnnouncementEntity;
import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.AlertTypeRepository;
import com.turbotikects.turbotikectsserver.repositorys.AnnouncementRepository;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupMemberRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnnouncementService {

    private static final String TRANSLATION_TYPE = "alert_types";

    private final AnnouncementRepository announcementRepo;
    private final AlertTypeRepository alertTypeRepo;
    private final DynamicTranslationsRepository dynamicTranslationsRepo;
    private final UserRepository userRepo;
    private final GroupRepository groupRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final EmailSenderService emailSenderService;
    private final TaskProgressService taskProgressService;

    public AnnouncementService(AnnouncementRepository announcementRepo,
                                AlertTypeRepository alertTypeRepo,
                                DynamicTranslationsRepository dynamicTranslationsRepo,
                                UserRepository userRepo,
                                GroupRepository groupRepo,
                                GroupMemberRepository groupMemberRepo,
                                EmailSenderService emailSenderService,
                                TaskProgressService taskProgressService) {
        this.announcementRepo = announcementRepo;
        this.alertTypeRepo = alertTypeRepo;
        this.dynamicTranslationsRepo = dynamicTranslationsRepo;
        this.userRepo = userRepo;
        this.groupRepo = groupRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.emailSenderService = emailSenderService;
        this.taskProgressService = taskProgressService;
    }

    public List<AnnouncementDto> getAll() {
        return announcementRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AnnouncementDto create(SaveAnnouncementRequestDto dto, Integer actorId) {
        validateSeverity(dto.getSeverity());
        AnnouncementEntity entity = new AnnouncementEntity();
        applyRequest(entity, dto);
        entity.setCreatedBy(actorId);
        entity = announcementRepo.save(entity);
        broadcastAsync(entity);
        return toDto(entity);
    }

    public AnnouncementDto update(Long id, SaveAnnouncementRequestDto dto) {
        validateSeverity(dto.getSeverity());
        AnnouncementEntity entity = announcementRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        applyRequest(entity, dto);
        entity = announcementRepo.save(entity);
        return toDto(entity);
    }

    public void resolve(Long id) {
        AnnouncementEntity entity = announcementRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        entity.setSeverity("resolved");
        entity.setResolvedAt(LocalDateTime.now());
        announcementRepo.save(entity);
    }

    public void delete(Long id) {
        if (!announcementRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
        announcementRepo.deleteById(id);
    }

    public List<PublicAnnouncementDto> getActivePublic(String lang) {
        return announcementRepo.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(e -> toPublicDto(e, lang))
                .collect(Collectors.toList());
    }

    private void validateSeverity(String severity) {
        if (severity == null || severity.isBlank() || !alertTypeRepo.existsByTypeKey(severity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown alert type: " + severity);
        }
    }

    private void applyRequest(AnnouncementEntity entity, SaveAnnouncementRequestDto dto) {
        entity.setSeverity(dto.getSeverity());
        entity.setTitle(dto.getTitle());
        entity.setMessage(dto.getMessage());
        entity.setShowOnPortal(Boolean.TRUE.equals(dto.getShowOnPortal()));
        entity.setShowOnTicketCreate(Boolean.TRUE.equals(dto.getShowOnTicketCreate()));
        entity.setShowOnAgentDashboard(Boolean.TRUE.equals(dto.getShowOnAgentDashboard()));
        entity.setIsActive(true);
        entity.setBroadcastEmail(Boolean.TRUE.equals(dto.getBroadcastEmail()));
        entity.setBroadcastTarget(dto.getBroadcastTarget());
        entity.setBroadcastGroupId(dto.getBroadcastGroupId());
    }

    private void broadcastAsync(AnnouncementEntity ann) {
        if (!Boolean.TRUE.equals(ann.getBroadcastEmail())) return;
        List<String> recipients = resolveRecipients(ann.getBroadcastTarget(), ann.getBroadcastGroupId());
        if (recipients.isEmpty()) return;

        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[Announcements] No default sender configured — skipping broadcast for announcement {}", ann.getId());
            return;
        }
        EmailMailboxEntity mailbox = mailboxOpt.get();
        String taskId = taskProgressService.createTask("Broadcasting: " + ann.getTitle(), recipients.size());

        new Thread(() -> {
            int sent = 0;
            try {
                for (String email : recipients) {
                    emailSenderService.sendReply(mailbox, email, ann.getTitle(), ann.getMessage(), null);
                    sent++;
                    taskProgressService.updateProgress(taskId, sent, "Sent to " + email);
                }
                taskProgressService.completeTask(taskId, "Broadcast complete — " + sent + " email(s) sent");
            } catch (Exception e) {
                log.error("[Announcements] Broadcast failed for announcement {}", ann.getId(), e);
                taskProgressService.failTask(taskId, e.getMessage());
            }
        }).start();
    }

    private List<String> resolveRecipients(String target, Integer groupId) {
        if ("group".equals(target) && groupId != null) {
            return groupMemberRepo.findByGroupId(groupId.longValue()).stream()
                    .map(m -> userRepo.findById(m.getUserId()).orElse(null))
                    .filter(u -> u != null && !u.isDeleted() && u.getEmail() != null && !u.getEmail().isBlank())
                    .map(UserEntity::getEmail)
                    .collect(Collectors.toList());
        }
        return userRepo.findAll().stream()
                .filter(u -> !u.isDeleted() && u.getEmail() != null && !u.getEmail().isBlank())
                .map(UserEntity::getEmail)
                .collect(Collectors.toList());
    }

    private AnnouncementDto toDto(AnnouncementEntity e) {
        AnnouncementDto dto = new AnnouncementDto();
        dto.setId(e.getId());
        dto.setSeverity(e.getSeverity());
        dto.setTitle(e.getTitle());
        dto.setMessage(e.getMessage());
        dto.setShowOnPortal(e.getShowOnPortal());
        dto.setShowOnTicketCreate(e.getShowOnTicketCreate());
        dto.setShowOnAgentDashboard(e.getShowOnAgentDashboard());
        dto.setIsActive(e.getIsActive());
        dto.setBroadcastEmail(e.getBroadcastEmail());
        dto.setBroadcastTarget(e.getBroadcastTarget());
        dto.setBroadcastGroupId(e.getBroadcastGroupId());
        if (e.getBroadcastGroupId() != null) {
            dto.setBroadcastGroupName(groupRepo.findById(e.getBroadcastGroupId().longValue())
                    .map(GroupEntity::getDisplayName).orElse(null));
        }
        if (e.getCreatedBy() != null) {
            dto.setCreatedByName(userRepo.findById(e.getCreatedBy().longValue())
                    .map(UserEntity::getDisplayName).orElse(null));
        }
        dto.setResolvedAt(e.getResolvedAt());
        dto.setExpiresAt(e.getExpiresAt());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private PublicAnnouncementDto toPublicDto(AnnouncementEntity e, String lang) {
        PublicAnnouncementDto dto = new PublicAnnouncementDto();
        dto.setId(e.getId());
        dto.setSeverity(e.getSeverity());
        dto.setTitle(e.getTitle());
        dto.setMessage(e.getMessage());
        dto.setShowOnPortal(e.getShowOnPortal());
        dto.setShowOnTicketCreate(e.getShowOnTicketCreate());
        dto.setShowOnAgentDashboard(e.getShowOnAgentDashboard());
        if (e.getCreatedBy() != null) {
            dto.setCreatedByName(userRepo.findById(e.getCreatedBy().longValue())
                    .map(UserEntity::getDisplayName).orElse(null));
        }
        dto.setCreatedAt(e.getCreatedAt());

        AlertTypeEntity alertType = alertTypeRepo.findByTypeKey(e.getSeverity()).orElse(null);
        if (alertType != null) {
            dto.setSeverityColor(alertType.getColor());
            dto.setSeverityIcon(alertType.getIcon());
        }
        dto.setSeverityLabel(resolveSeverityLabel(e.getSeverity(), lang));
        return dto;
    }

    private String resolveSeverityLabel(String severity, String lang) {
        if (lang != null && !"en".equals(lang)) {
            Optional<String> localized = dynamicTranslationsRepo
                    .findByLangCodeAndTypeAndTranslationKey(lang, TRANSLATION_TYPE, severity)
                    .map(t -> t.getTranslatedText())
                    .filter(text -> text != null && !text.isBlank());
            if (localized.isPresent()) return localized.get();
        }
        return dynamicTranslationsRepo
                .findByLangCodeAndTypeAndTranslationKey("en", TRANSLATION_TYPE, severity)
                .map(t -> t.getTranslatedText())
                .orElse(severity);
    }
}
