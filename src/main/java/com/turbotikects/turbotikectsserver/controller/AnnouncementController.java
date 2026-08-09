package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AnnouncementDto;
import com.turbotikects.turbotikectsserver.dto.SaveAnnouncementRequestDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.AnnouncementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@RequirePermission("MANAGE_ANNOUNCEMENTS")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<AnnouncementDto> getAll() {
        return announcementService.getAll();
    }

    @PostMapping
    public AnnouncementDto create(@RequestBody SaveAnnouncementRequestDto dto, HttpServletRequest request) {
        return announcementService.create(dto, currentUserId(request));
    }

    @PutMapping("/{id}")
    public AnnouncementDto update(@PathVariable Long id, @RequestBody SaveAnnouncementRequestDto dto) {
        return announcementService.update(id, dto);
    }

    @PatchMapping("/{id}/resolve")
    public void resolve(@PathVariable Long id) {
        announcementService.resolve(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        announcementService.delete(id);
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }
}
