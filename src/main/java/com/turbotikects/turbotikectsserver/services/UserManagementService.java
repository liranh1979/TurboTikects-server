package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.CreateUserDto;
import com.turbotikects.turbotikectsserver.dto.UpdateUserDto;
import com.turbotikects.turbotikectsserver.dto.UserListItemDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
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

    public List<UserListItemDto> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        List<Long> userIds = users.stream().map(UserEntity::getRed_id).toList();
        Map<Long, List<String>> permMap = permissionService.getPersonalPermissionsForUsers(userIds);

        return users.stream().map(u -> {
            UserListItemDto dto = new UserListItemDto();
            dto.setId(u.getRed_id());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getDisplayName());
            dto.setSuperAdmin(u.isSuperAdmin());
            dto.setMetadata(u.getMetadata());
            dto.setPersonalPermissions(permMap.getOrDefault(u.getRed_id(), List.of()));
            return dto;
        }).toList();
    }

    public UserListItemDto createUser(CreateUserDto dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        if (dto.getPassword() == null || dto.getPassword().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");

        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername().trim().toLowerCase());
        user.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName().trim() : dto.getUsername());
        user.setSuperAdmin(false);
        user.setMetadata(new HashMap<>());
        user.setPassword(HashUtils.sha1(dto.getUsername().trim().toLowerCase() + "_" + dto.getPassword()));
        userRepository.save(user);

        UserListItemDto result = new UserListItemDto();
        result.setId(user.getRed_id());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setSuperAdmin(false);
        result.setMetadata(user.getMetadata());
        result.setPersonalPermissions(List.of());
        return result;
    }

    public UserListItemDto updateUser(Long id, UpdateUserDto dto) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank())
            user.setDisplayName(dto.getDisplayName().trim());

        if (dto.getPassword() != null && !dto.getPassword().isBlank())
            user.setPassword(HashUtils.sha1(user.getUsername() + "_" + dto.getPassword()));

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
        result.setSuperAdmin(user.isSuperAdmin());
        result.setMetadata(user.getMetadata());
        result.setPersonalPermissions(permissionService.getPersonalPermissions(id));
        return result;
    }

    public void deleteUser(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isSuperAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin users cannot be deleted");
        userRepository.delete(user);
    }

    public String startSyncMetadata() {
        List<UserEntity> users = userRepository.findAll();
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