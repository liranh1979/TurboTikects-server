package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.AdminNotificationDto;
import com.turbotikects.turbotikectsserver.entitys.AdminNotificationEntity;
import com.turbotikects.turbotikectsserver.repositorys.AdminNotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminInboxService {

    private final AdminNotificationRepository repo;

    public AdminInboxService(AdminNotificationRepository repo) {
        this.repo = repo;
    }

    public List<AdminNotificationDto> getRecent() {
        return repo.findTop50ByOrderByCreatedAtDesc().stream().map(this::toDto).collect(Collectors.toList());
    }

    public Map<String, Long> getUnreadCount() {
        return Map.of("count", repo.countByIsReadFalse());
    }

    public void markRead(Long id) {
        AdminNotificationEntity n = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        n.setRead(true);
        repo.save(n);
    }

    public void markAllRead() {
        repo.markAllRead();
    }

    /** Creates an in-app notification only if one hasn't been sent already for this ticket+field+type. */
    public void createIfAbsent(Long ticketId, String fieldKey, String type, String message) {
        if (!repo.existsByTicketIdAndFieldKeyAndNotificationType(ticketId, fieldKey, type)) {
            AdminNotificationEntity n = new AdminNotificationEntity();
            n.setTicketId(ticketId);
            n.setFieldKey(fieldKey);
            n.setNotificationType(type);
            n.setMessage(message);
            n.setCreatedAt(LocalDateTime.now());
            repo.save(n);
        }
    }

    private AdminNotificationDto toDto(AdminNotificationEntity e) {
        AdminNotificationDto dto = new AdminNotificationDto();
        dto.setId(e.getId());
        dto.setTicketId(e.getTicketId());
        dto.setFieldKey(e.getFieldKey());
        dto.setNotificationType(e.getNotificationType());
        dto.setMessage(e.getMessage());
        dto.setRead(e.isRead());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
