package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.CsatSubmitRequestDto;
import com.turbotikects.turbotikectsserver.dto.CsatSurveyDto;
import com.turbotikects.turbotikectsserver.services.CsatService;
import org.springframework.web.bind.annotation.*;

// Public, unauthenticated — deliberately under /csat-survey/** (not /csat/**) so the
// wildcard exclusion in WebConfig.java can't accidentally make the authenticated
// /api/v1/csat/dashboard endpoint public too.
@RestController
@RequestMapping("/api/v1/csat-survey")
public class CsatController {

    private final CsatService csatService;

    public CsatController(CsatService csatService) {
        this.csatService = csatService;
    }

    @GetMapping("/{token}")
    public CsatSurveyDto getSurvey(@PathVariable String token) {
        return csatService.getSurveyByToken(token);
    }

    @PostMapping("/{token}")
    public void submit(@PathVariable String token, @RequestBody CsatSubmitRequestDto dto) {
        csatService.submitResponse(token, dto);
    }
}
