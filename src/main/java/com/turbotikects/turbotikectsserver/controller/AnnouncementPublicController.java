package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.PublicAnnouncementDto;
import com.turbotikects.turbotikectsserver.services.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements-public")
public class AnnouncementPublicController {

    private final AnnouncementService announcementService;

    public AnnouncementPublicController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/active")
    public List<PublicAnnouncementDto> getActive(
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return announcementService.getActivePublic(primaryLang(acceptLanguage));
    }

    private String primaryLang(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return "en";
        String primary = acceptLanguage.split(",")[0].trim();
        int dash = primary.indexOf('-');
        return (dash > 0 ? primary.substring(0, dash) : primary).toLowerCase();
    }
}
