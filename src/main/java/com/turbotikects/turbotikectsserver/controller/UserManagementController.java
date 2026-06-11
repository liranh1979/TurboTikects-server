package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.CreateUserDto;
import com.turbotikects.turbotikectsserver.dto.UpdateUserDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.UserListItemDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequirePermission("MANAGE_USERS")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public List<UserListItemDto> getAllUsers(HttpServletRequest request) {
        UserDto caller = (UserDto) request.getAttribute("currentUser");
        Integer companyId = (caller != null && !caller.isSuperAdmin()) ? caller.getCompanyId() : null;
        return userManagementService.getAllUsers(companyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserListItemDto createUser(@RequestBody CreateUserDto dto, HttpServletRequest request) {
        UserDto caller = (UserDto) request.getAttribute("currentUser");
        Integer callerCompanyId = (caller != null && !caller.isSuperAdmin()) ? caller.getCompanyId() : null;
        return userManagementService.createUser(dto, callerCompanyId);
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