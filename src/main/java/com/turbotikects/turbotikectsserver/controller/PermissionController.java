package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.PermissionDto;
import com.turbotikects.turbotikectsserver.services.PermissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<PermissionDto> getAllPermissions() {
        return permissionService.getAllPermissions();
    }
}
