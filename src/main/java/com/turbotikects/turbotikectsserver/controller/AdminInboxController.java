package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AdminNotificationDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.AdminInboxService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin-inbox")
@RequirePermission("AUTHENTICATED")
public class AdminInboxController {

    private final AdminInboxService service;

    public AdminInboxController(AdminInboxService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminNotificationDto> getRecent() {
        return service.getRecent();
    }

    @GetMapping("/count")
    public Map<String, Long> getUnreadCount() {
        return service.getUnreadCount();
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        service.markRead(id);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        service.markAllRead();
    }
}
