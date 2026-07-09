package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.UserNotificationDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.UserNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Every method resolves the target user from the session ("currentUser" request
// attribute) only — never from a path/body-supplied id — so there is no way to
// read or mark another user's notifications. Same pattern as UserProfileController.
@RestController
@RequestMapping("/api/v1/notifications")
@RequirePermission("AUTHENTICATED")
public class UserNotificationController {

    private final UserNotificationService userNotificationService;

    public UserNotificationController(UserNotificationService userNotificationService) {
        this.userNotificationService = userNotificationService;
    }

    private Long callerId(HttpServletRequest req) {
        UserDto caller = (UserDto) req.getAttribute("currentUser");
        return caller.getUserId();
    }

    @GetMapping
    public List<UserNotificationDto> getRecent(HttpServletRequest req) {
        return userNotificationService.listForUser(callerId(req));
    }

    @GetMapping("/count")
    public Map<String, Long> getUnreadCount(HttpServletRequest req) {
        return userNotificationService.unreadCount(callerId(req));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, HttpServletRequest req) {
        userNotificationService.markRead(id, callerId(req));
    }

    @PostMapping("/read-all")
    public void markAllRead(HttpServletRequest req) {
        userNotificationService.markAllRead(callerId(req));
    }
}
