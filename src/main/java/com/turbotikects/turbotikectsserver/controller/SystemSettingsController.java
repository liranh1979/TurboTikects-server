package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.MissingTranslationsDto;
import com.turbotikects.turbotikectsserver.dto.SystemSettingsDto;
import com.turbotikects.turbotikectsserver.dto.UpdateSystemSettingsDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.services.SystemSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/system-settings")
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    public SystemSettingsController(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    private void requireSuperAdmin(HttpServletRequest req) {
        UserDto caller = (UserDto) req.getAttribute("currentUser");
        if (caller == null || !caller.isSuperAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin only");
    }

    // Public — fetched before login (login page logo/language) and on every app bootstrap.
    @GetMapping
    public SystemSettingsDto get() {
        return systemSettingsService.getPublicSettings();
    }

    // NOTE: deliberately a distinct path from the public GET above (not PATCH on the same
    // URL) — WebConfig excludes "/api/v1/system-settings" from auth by path only, with no
    // HTTP-method awareness, so an update verb on that exact path would have been public too.
    @PostMapping("/update")
    public SystemSettingsDto update(@RequestBody UpdateSystemSettingsDto dto, HttpServletRequest req) {
        requireSuperAdmin(req);
        return systemSettingsService.updateSettings(dto);
    }

    // Distinct from "/logo" (GET, public) for the same reason as "/update" above.
    @PostMapping(value = "/logo/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SystemSettingsDto uploadLogo(@RequestParam("file") MultipartFile file, HttpServletRequest req) throws IOException {
        requireSuperAdmin(req);
        return systemSettingsService.uploadLogo(file);
    }

    // Public — same stable URL is embedded in SystemSettingsDto.logoUrl and rendered on the
    // pre-login screen, so it cannot require a session cookie or a signed token.
    @GetMapping("/logo")
    public ResponseEntity<ByteArrayResource> logo() throws IOException {
        byte[] data = systemSettingsService.getLogoBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(systemSettingsService.getLogoContentType()))
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    // Returns the full missing-entry list (type + key + englishText), not just a count/sample —
    // the client batches these itself via /ai/translate-bulk (~20 at a time) and drives its own
    // progress bar + partial-save-on-failure, the same way the System Fields / custom-field
    // grids already do. There's no corresponding "translate missing" backend endpoint anymore.
    @GetMapping("/missing-translations")
    public MissingTranslationsDto missingTranslations(@RequestParam String lang, HttpServletRequest req) {
        requireSuperAdmin(req);
        return systemSettingsService.getMissingTranslations(lang);
    }
}
