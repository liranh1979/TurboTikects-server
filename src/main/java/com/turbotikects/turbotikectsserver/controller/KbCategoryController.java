package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.KbCategoryDto;
import com.turbotikects.turbotikectsserver.dto.SaveKbCategoryRequestDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.KbCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kb-categories")
@RequirePermission({"MANAGE_KNOWLEDGE_BASE", "TICKET_MANAGER"})
public class KbCategoryController {

    private final KbCategoryService kbCategoryService;

    public KbCategoryController(KbCategoryService kbCategoryService) {
        this.kbCategoryService = kbCategoryService;
    }

    @GetMapping
    @RequirePermission("AUTHENTICATED")
    public List<KbCategoryDto> getAll() {
        return kbCategoryService.getAll();
    }

    @PostMapping
    public KbCategoryDto create(@RequestBody SaveKbCategoryRequestDto dto) {
        return kbCategoryService.create(dto);
    }

    @PutMapping("/{id}")
    public KbCategoryDto update(@PathVariable Long id, @RequestBody SaveKbCategoryRequestDto dto) {
        return kbCategoryService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        kbCategoryService.delete(id);
    }
}
