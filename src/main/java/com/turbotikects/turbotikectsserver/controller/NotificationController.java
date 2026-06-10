package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.NotificationTemplateDto;
import com.turbotikects.turbotikectsserver.dto.UpdateNotificationTemplateDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequirePermission("MANAGE_NOTIFICATIONS")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/templates")
    public List<NotificationTemplateDto> getAll() {
        return notificationService.getAllTemplates();
    }

    @PatchMapping("/templates/{id}")
    public NotificationTemplateDto update(@PathVariable Long id,
                                          @RequestBody UpdateNotificationTemplateDto dto) {
        return notificationService.updateTemplate(id, dto);
    }

    @PostMapping("/templates/{id}/rollback")
    public NotificationTemplateDto rollback(@PathVariable Long id) {
        return notificationService.rollbackToDefault(id);
    }
}
