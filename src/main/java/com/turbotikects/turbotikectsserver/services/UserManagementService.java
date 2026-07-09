package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.CreateUserDto;
import com.turbotikects.turbotikectsserver.dto.UpdateUserDto;
import com.turbotikects.turbotikectsserver.dto.UserListItemDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    @Autowired private TicketRepository ticketRepository;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final TaskProgressService taskProgressService;
    private final PermissionService permissionService;

    public UserManagementService(UserRepository userRepository,
                                 FieldDefinitionsRepository fieldDefinitionsRepository,
                                 TaskProgressService taskProgressService,
                                 PermissionService permissionService) {
        this.userRepository = userRepository;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.taskProgressService = taskProgressService;
        this.permissionService = permissionService;
    }

    public List<UserListItemDto> getAllUsers(Integer callerCompanyId) {
        List<UserEntity> users = callerCompanyId != null
                ? userRepository.findByCompanyId(callerCompanyId)
                : userRepository.findByIsDeletedFalse();
        List<Long> userIds = users.stream().map(UserEntity::getRed_id).toList();
        Map<Long, List<String>> permMap = permissionService.getPersonalPermissionsForUsers(userIds);

        return users.stream().map(u -> {
            UserListItemDto dto = new UserListItemDto();
            dto.setId(u.getRed_id());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getDisplayName());
            dto.setEmail(u.getEmail());
            dto.setSuperAdmin(u.isSuperAdmin());
            dto.setMetadata(u.getMetadata());
            dto.setPersonalPermissions(permMap.getOrDefault(u.getRed_id(), List.of()));
            dto.setCompanyId(u.getCompanyId());
            return dto;
        }).toList();
    }

    public UserListItemDto createUser(CreateUserDto dto, Integer callerCompanyId) {
        if (dto.getUsername() == null || dto.getUsername().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        if (dto.getPassword() == null || dto.getPassword().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");

        String username = dto.getUsername().trim().toLowerCase();

        // Reactivate a soft-deleted user with the same username instead of inserting a new row
        // (the user_name column has a UNIQUE constraint).
        UserEntity user = userRepository.findByUsername(username)
                .filter(UserEntity::isDeleted)
                .orElseGet(UserEntity::new);
        boolean reactivating = user.getRed_id() != null;

        user.setUsername(username);
        user.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName().trim() : dto.getUsername());
        user.setSuperAdmin(false);
        user.setMetadata(new HashMap<>());
        user.setPassword(HashUtils.bcrypt(dto.getPassword()));
        user.setEmail((dto.getEmail() != null && !dto.getEmail().isBlank()) ? dto.getEmail().trim().toLowerCase() : null);
        user.setCompanyId(callerCompanyId);
        user.setDeleted(false);
        userRepository.save(user);

        if (reactivating) {
            // Drop stale personal permissions from the previous account
            permissionService.setUserPermissions(user.getRed_id(), List.of());
        }

        UserListItemDto result = new UserListItemDto();
        result.setId(user.getRed_id());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setEmail(user.getEmail());
        result.setSuperAdmin(false);
        result.setMetadata(user.getMetadata());
        result.setPersonalPermissions(List.of());
        result.setCompanyId(user.getCompanyId());
        return result;
    }

    public UserListItemDto updateUser(Long id, UpdateUserDto dto) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank())
            user.setDisplayName(dto.getDisplayName().trim());

        // Only local accounts have a real, checkable password — LDAP/Azure users authenticate
        // via bind/ROPC, so their real password isn't known to this system at all.
        if (dto.getPassword() != null && !dto.getPassword().isBlank() && user.getSourceType() == 0)
            user.setPassword(HashUtils.bcrypt(dto.getPassword()));

        if (dto.getEmail() != null)
            user.setEmail(dto.getEmail().isBlank() ? null : dto.getEmail().trim().toLowerCase());

        if (dto.getCompanyId() != null) {
            Integer newCompanyId = dto.getCompanyId() == -1 ? null : dto.getCompanyId();
            user.setCompanyId(newCompanyId);
            // Retroactively stamp existing tickets opened by this user with the new company
            ticketRepository.updateCompanyForUser(id.intValue(), newCompanyId);
        }

        if (dto.getMetadata() != null) {
            Map<String, Object> merged = user.getMetadata() != null
                    ? new HashMap<>(user.getMetadata()) : new HashMap<>();
            merged.putAll(dto.getMetadata());
            user.setMetadata(merged);
        }

        userRepository.save(user);

        if (dto.getPermissions() != null) {
            permissionService.setUserPermissions(id, dto.getPermissions());
        }

        UserListItemDto result = new UserListItemDto();
        result.setId(user.getRed_id());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setEmail(user.getEmail());
        result.setSuperAdmin(user.isSuperAdmin());
        result.setMetadata(user.getMetadata());
        result.setPersonalPermissions(permissionService.getPersonalPermissions(id));
        result.setCompanyId(user.getCompanyId());
        return result;
    }

    public void deleteUser(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isSuperAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin users cannot be deleted");
        // Soft delete: tickets reference users via fk_ticket_request_user, so a hard delete
        // would fail for any user that has ever created a ticket. Flag the row instead so it
        // disappears from all user lists/pickers; a re-created account with the same
        // user_name reactivates this row (see createUser).
        user.setDeleted(true);
        userRepository.save(user);
    }

    public String startSyncMetadata() {
        List<UserEntity> users = userRepository.findByIsDeletedFalse();
        List<FieldDefinitionsEntity> fields = fieldDefinitionsRepository
                .findByEntityTypeAndIsSystemFalseOrderByDisplayOrder("user");

        String taskId = taskProgressService.createTask("Sync User Metadata", users.size());

        new Thread(() -> {
            try {
                for (int i = 0; i < users.size(); i++) {
                    UserEntity user = users.get(i);
                    Map<String, Object> metadata = user.getMetadata() != null
                            ? new HashMap<>(user.getMetadata())
                            : new HashMap<>();

                    boolean changed = false;
                    for (FieldDefinitionsEntity field : fields) {
                        if (!metadata.containsKey(field.getFieldKey())) {
                            Map<String, Object> defaultValue = new HashMap<>();
                            defaultValue.put("value", "");
                            defaultValue.put("translation_key", field.getFieldKey());
                            defaultValue.put("view_position", field.getDisplayOrder());
                            metadata.put(field.getFieldKey(), defaultValue);
                            changed = true;
                        }
                    }

                    if (changed) {
                        user.setMetadata(metadata);
                        userRepository.save(user);
                    }

                    taskProgressService.updateProgress(taskId, i + 1,
                            "Processing user: " + user.getDisplayName());

                    Thread.sleep(100); // slight delay so the progress bar is visible
                }
                taskProgressService.completeTask(taskId,
                        "Synced metadata for " + users.size() + " users");
            } catch (Exception e) {
                taskProgressService.failTask(taskId, "Error: " + e.getMessage());
            }
        }).start();

        return taskId;
    }
}