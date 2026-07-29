package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.SaveActionItemLibraryRequestDto;
import com.turbotikects.turbotikectsserver.entitys.ActionItemLibraryEntity;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.ActionItemLibraryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/action-item-library")
@RequirePermission("MANAGE_FIELDS")
public class ActionItemLibraryController {

    private final ActionItemLibraryService service;

    public ActionItemLibraryController(ActionItemLibraryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ActionItemLibraryEntity> getAll(@RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String status) {
        return service.getAll(type, status);
    }

    @PostMapping
    public ActionItemLibraryEntity create(@RequestBody SaveActionItemLibraryRequestDto req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ActionItemLibraryEntity update(@PathVariable Long id, @RequestBody SaveActionItemLibraryRequestDto req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
