package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    // Open to any authenticated user — needed to render ticket forms for end users
    @GetMapping("/{id}")
    public TemplateWithLayoutDto getWithLayout(@PathVariable Long id) {
        return templateService.getWithLayout(id);
    }

    // Open to any authenticated user — needed for template selection in CreateTicketPage
    @GetMapping
    public List<TemplateSummaryDto> getAll() {
        return templateService.getAll();
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping
    public TemplateWithLayoutDto create(@RequestBody SaveLayoutRequestDto dto) {
        return templateService.create(dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PutMapping("/{id}")
    public TemplateWithLayoutDto saveLayout(@PathVariable Long id, @RequestBody SaveLayoutRequestDto dto) {
        return templateService.saveLayout(id, dto);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PatchMapping("/{id}/set-default")
    public TemplateSummaryDto setDefault(@PathVariable Long id) {
        return templateService.setDefault(id);
    }

    @RequirePermission("MANAGE_FIELDS")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }

    @RequirePermission("MANAGE_FIELDS")
    @PostMapping("/{id}/ai-suggest")
    public Map<String, Object> aiSuggest(@PathVariable Long id,
                                         @RequestBody AiSuggestLayoutRequestDto dto)
            throws URISyntaxException, IOException, InterruptedException {
        return templateService.aiSuggestLayout(id, dto);
    }
}
