package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.SslInfoDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.services.SslSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ssl")
public class SslSettingsController {

    private final SslSettingsService sslSettingsService;

    public SslSettingsController(SslSettingsService sslSettingsService) {
        this.sslSettingsService = sslSettingsService;
    }

    private void requireSuperAdmin(HttpServletRequest req) {
        UserDto caller = (UserDto) req.getAttribute("currentUser");
        if (caller == null || !caller.isSuperAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin only");
    }

    @GetMapping("/info")
    public SslInfoDto getInfo(HttpServletRequest req) {
        requireSuperAdmin(req);
        return sslSettingsService.getInfo();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SslInfoDto upload(
            @RequestParam(required = false) String certType,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "3443") int httpsPort,
            @RequestParam(required = false) MultipartFile certFile,
            @RequestParam(required = false) MultipartFile keyFile,
            @RequestParam(required = false) MultipartFile p12File,
            @RequestParam(required = false) String p12Password,
            HttpServletRequest req) {
        requireSuperAdmin(req);
        return sslSettingsService.uploadCertificate(certType, domain, httpsPort,
                certFile, keyFile, p12File, p12Password);
    }

    @DeleteMapping
    public void remove(HttpServletRequest req) {
        requireSuperAdmin(req);
        sslSettingsService.removeCertificate();
    }
}
