package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AboutDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/about")
public class AboutController {

    @Value("${app.version:dev}")
    private String serverVersion;

    @GetMapping
    public AboutDto get() {
        return new AboutDto(serverVersion);
    }
}
