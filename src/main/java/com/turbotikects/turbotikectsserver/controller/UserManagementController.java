package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.CreateUserDto;
import com.turbotikects.turbotikectsserver.dto.UpdateUserDto;
import com.turbotikects.turbotikectsserver.dto.UserListItemDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.UserManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "${app.cors.origins}")
@RestController
@RequestMapping("/api/v1/users")
@RequirePermission("MANAGE_USERS")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public List<UserListItemDto> getAllUsers() {
        return userManagementService.getAllUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserListItemDto createUser(@RequestBody CreateUserDto dto) {
        return userManagementService.createUser(dto);
    }

    @PatchMapping("/{id}")
    public UserListItemDto updateUser(@PathVariable Long id, @RequestBody UpdateUserDto dto) {
        return userManagementService.updateUser(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userManagementService.deleteUser(id);
    }

    @PostMapping("/sync-metadata")
    public Map<String, String> syncMetadata() {
        String taskId = userManagementService.startSyncMetadata();
        return Map.of("taskId", taskId);
    }
}