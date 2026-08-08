package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.RecurringScheduleDto;
import com.turbotikects.turbotikectsserver.dto.SaveRecurringScheduleRequestDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.RecurringScheduleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recurring-schedules")
@RequirePermission("MANAGE_RECURRING_TICKETS")
public class RecurringScheduleController {

    private final RecurringScheduleService service;

    public RecurringScheduleController(RecurringScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecurringScheduleDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public RecurringScheduleDto create(@RequestBody SaveRecurringScheduleRequestDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public RecurringScheduleDto update(@PathVariable Long id, @RequestBody SaveRecurringScheduleRequestDto dto) {
        return service.update(id, dto);
    }

    @PatchMapping("/{id}/active")
    public void setActive(@PathVariable Long id, @RequestParam boolean active) {
        service.setActive(id, active);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/groups")
    public List<Map<String, Object>> getAssignableGroups() {
        return service.getAssignableGroups();
    }
}
