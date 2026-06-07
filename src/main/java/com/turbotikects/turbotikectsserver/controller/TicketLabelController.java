package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.SaveTicketLabelRequestDto;
import com.turbotikects.turbotikectsserver.dto.TicketLabelDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.TicketLabelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ticket-labels")
@RequirePermission("MANAGE_FIELDS")
public class TicketLabelController {

    private final TicketLabelService service;

    public TicketLabelController(TicketLabelService service) {
        this.service = service;
    }

    @GetMapping
    public List<TicketLabelDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public TicketLabelDto create(@RequestBody SaveTicketLabelRequestDto req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public TicketLabelDto update(@PathVariable Long id, @RequestBody SaveTicketLabelRequestDto req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @DeleteMapping("/bulk")
    public void bulkDelete(@RequestBody Map<String, List<Long>> body) {
        service.bulkDelete(body.get("ids"));
    }
}
