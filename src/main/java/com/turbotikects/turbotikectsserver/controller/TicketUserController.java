package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.UserSelectItemDto;
import com.turbotikects.turbotikectsserver.entitys.GroupMemberEntity;
import com.turbotikects.turbotikectsserver.entitys.GroupPermissionEntity;
import com.turbotikects.turbotikectsserver.entitys.PermissionEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides lightweight user lists for ticket field pickers.
 * Requires authentication (AuthInterceptor) but no specific permission.
 */
@RestController
@RequestMapping("/api/v1/ticket-users")
public class TicketUserController {

    private final UserRepository userRepo;
    private final PermissionRepository permissionRepo;
    private final UserPermissionRepository userPermRepo;
    private final GroupPermissionRepository groupPermRepo;
    private final GroupMemberRepository groupMemberRepo;

    public TicketUserController(UserRepository userRepo,
                                PermissionRepository permissionRepo,
                                UserPermissionRepository userPermRepo,
                                GroupPermissionRepository groupPermRepo,
                                GroupMemberRepository groupMemberRepo) {
        this.userRepo = userRepo;
        this.permissionRepo = permissionRepo;
        this.userPermRepo = userPermRepo;
        this.groupPermRepo = groupPermRepo;
        this.groupMemberRepo = groupMemberRepo;
    }

    /** All users (in caller's company, if scoped) — for request_user picker */
    @GetMapping("/assignable")
    public List<UserSelectItemDto> getAssignable(HttpServletRequest request) {
        Integer companyId = callerCompanyId(request);
        List<UserEntity> users = companyId != null
                ? userRepo.findByCompanyId(companyId)
                : userRepo.findByIsDeletedFalse();
        return users.stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(UserSelectItemDto::getDisplayName,
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /** Super admins + users with effective TICKET_MANAGER permission — for responsible picker */
    @GetMapping("/managers")
    public List<UserSelectItemDto> getManagers(HttpServletRequest request) {
        Integer companyId = callerCompanyId(request);
        Set<Long> managerIds = new HashSet<>();

        // Super admins always qualify
        userRepo.findByIsDeletedFalse().stream()
                .filter(UserEntity::isSuperAdmin)
                .map(UserEntity::getRed_id)
                .forEach(managerIds::add);

        // Users/groups with TICKET_MANAGER permission
        Optional<PermissionEntity> permOpt = permissionRepo.findByPermissionKey("TICKET_MANAGER");
        permOpt.ifPresent(perm -> {
            Long permId = perm.getId();

            // Direct user permissions
            userPermRepo.findByPermissionId(permId)
                    .forEach(up -> managerIds.add(up.getUserId()));

            // Group permissions → group members
            List<Long> groupIds = groupPermRepo.findByPermissionId(permId).stream()
                    .map(GroupPermissionEntity::getGroupId)
                    .collect(Collectors.toList());
            if (!groupIds.isEmpty()) {
                groupMemberRepo.findByGroupIdIn(groupIds)
                        .forEach(gm -> managerIds.add(gm.getUserId()));
            }
        });

        return userRepo.findAllById(new ArrayList<>(managerIds)).stream()
                .filter(u -> !u.isDeleted())
                .filter(u -> companyId == null
                        || (u.getCompanyId() != null && u.getCompanyId().equals(companyId)))
                .map(this::toDto)
                .sorted(Comparator.comparing(UserSelectItemDto::getDisplayName,
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private Integer callerCompanyId(HttpServletRequest request) {
        UserDto caller = (UserDto) request.getAttribute("currentUser");
        return (caller != null && !caller.isSuperAdmin()) ? caller.getCompanyId() : null;
    }

    private UserSelectItemDto toDto(UserEntity u) {
        UserSelectItemDto dto = new UserSelectItemDto();
        dto.setUserId(u.getRed_id());
        dto.setDisplayName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
        return dto;
    }
}
