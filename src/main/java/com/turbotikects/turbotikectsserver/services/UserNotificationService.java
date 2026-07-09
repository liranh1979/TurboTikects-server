package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.UserNotificationDto;
import com.turbotikects.turbotikectsserver.entitys.UserNotificationEntity;
import com.turbotikects.turbotikectsserver.repositorys.UserNotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserNotificationService {

    private final UserNotificationRepository repo;

    public UserNotificationService(UserNotificationRepository repo) {
        this.repo = repo;
    }

    public void create(Long recipientUserId, Long ticketId, String type, String message, String linkUrl) {
        UserNotificationEntity n = new UserNotificationEntity();
        n.setRecipientUserId(recipientUserId);
        n.setTicketId(ticketId);
        n.setNotificationType(type);
        n.setMessage(message);
        n.setLinkUrl(linkUrl);
        n.setCreatedAt(LocalDateTime.now());
        repo.save(n);
    }

    public List<UserNotificationDto> listForUser(Long userId) {
        return repo.findTop50ByRecipientUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public Map<String, Long> unreadCount(Long userId) {
        return Map.of("count", repo.countByRecipientUserIdAndIsReadFalse(userId));
    }

    public void markRead(Long id, Long userId) {
        UserNotificationEntity n = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!n.getRecipientUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        n.setRead(true);
        repo.save(n);
    }

    public void markAllRead(Long userId) {
        repo.markAllRead(userId);
    }

    private UserNotificationDto toDto(UserNotificationEntity e) {
        UserNotificationDto dto = new UserNotificationDto();
        dto.setId(e.getId());
        dto.setTicketId(e.getTicketId());
        dto.setNotificationType(e.getNotificationType());
        dto.setMessage(e.getMessage());
        dto.setLinkUrl(e.getLinkUrl());
        dto.setRead(e.isRead());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
