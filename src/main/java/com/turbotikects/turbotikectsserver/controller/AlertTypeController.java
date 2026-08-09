package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AlertTypeDto;
import com.turbotikects.turbotikectsserver.dto.SaveAlertTypeRequestDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.AlertTypeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alert-types")
@RequirePermission({"MANAGE_FIELDS", "MANAGE_LANGUAGES"})
public class AlertTypeController {

    private final AlertTypeService alertTypeService;

    public AlertTypeController(AlertTypeService alertTypeService) {
        this.alertTypeService = alertTypeService;
    }

    @GetMapping
    @RequirePermission("AUTHENTICATED")
    public List<AlertTypeDto> getAll() {
        return alertTypeService.getAll();
    }

    @PostMapping
    public AlertTypeDto create(@RequestBody SaveAlertTypeRequestDto dto) {
        return alertTypeService.create(dto);
    }

    @PutMapping("/{id}")
    public AlertTypeDto update(@PathVariable Long id, @RequestBody SaveAlertTypeRequestDto dto) {
        return alertTypeService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        alertTypeService.delete(id);
    }
}
