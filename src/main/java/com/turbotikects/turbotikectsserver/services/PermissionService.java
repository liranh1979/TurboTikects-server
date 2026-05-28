package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.PermissionDto;
import com.turbotikects.turbotikectsserver.entitys.GroupPermissionEntity;
import com.turbotikects.turbotikectsserver.entitys.PermissionEntity;
import com.turbotikects.turbotikectsserver.entitys.UserPermissionEntity;
import com.turbotikects.turbotikectsserver.repositorys.GroupPermissionRepository;
import com.turbotikects.turbotikectsserver.repositorys.PermissionRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final GroupPermissionRepository groupPermissionRepository;

    public PermissionService(PermissionRepository permissionRepository,
                             UserPermissionRepository userPermissionRepository,
                             GroupPermissionRepository groupPermissionRepository) {
        this.permissionRepository = permissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.groupPermissionRepository = groupPermissionRepository;
    }

    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAllByOrderByDisplayOrder().stream()
                .map(this::toDto)
                .toList();
    }

    public List<String> computeEffectivePermissions(Long userId) {
        return permissionRepository.findEffectivePermissionKeysByUserId(userId);
    }

    public List<String> getPersonalPermissions(Long userId) {
        List<UserPermissionEntity> rows = userPermissionRepository.findByUserId(userId);
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = rows.stream().map(UserPermissionEntity::getPermissionId).collect(Collectors.toSet());
        return permissionRepository.findAllById(ids).stream()
                .map(PermissionEntity::getPermissionKey)
                .toList();
    }

    public List<String> getGroupPermissions(Long groupId) {
        List<GroupPermissionEntity> rows = groupPermissionRepository.findByGroupId(groupId);
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = rows.stream().map(GroupPermissionEntity::getPermissionId).collect(Collectors.toSet());
        return permissionRepository.findAllById(ids).stream()
                .map(PermissionEntity::getPermissionKey)
                .toList();
    }

    /** Batch: returns map of userId → list of personal permission keys (avoids N+1). */
    public Map<Long, List<String>> getPersonalPermissionsForUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<UserPermissionEntity> rows = userPermissionRepository.findByUserIdIn(userIds);

        Set<Long> permIds = rows.stream().map(UserPermissionEntity::getPermissionId).collect(Collectors.toSet());
        Map<Long, String> permIdToKey = permissionRepository.findAllById(permIds).stream()
                .collect(Collectors.toMap(PermissionEntity::getId, PermissionEntity::getPermissionKey));

        Map<Long, List<String>> result = new HashMap<>();
        for (UserPermissionEntity row : rows) {
            result.computeIfAbsent(row.getUserId(), k -> new ArrayList<>())
                    .add(permIdToKey.getOrDefault(row.getPermissionId(), ""));
        }
        return result;
    }

    /** Batch: returns map of groupId → list of permission keys (avoids N+1). */
    public Map<Long, List<String>> getGroupPermissionsForGroups(List<Long> groupIds) {
        if (groupIds.isEmpty()) return Map.of();
        List<GroupPermissionEntity> rows = groupPermissionRepository.findByGroupIdIn(groupIds);

        Set<Long> permIds = rows.stream().map(GroupPermissionEntity::getPermissionId).collect(Collectors.toSet());
        Map<Long, String> permIdToKey = permissionRepository.findAllById(permIds).stream()
                .collect(Collectors.toMap(PermissionEntity::getId, PermissionEntity::getPermissionKey));

        Map<Long, List<String>> result = new HashMap<>();
        for (GroupPermissionEntity row : rows) {
            result.computeIfAbsent(row.getGroupId(), k -> new ArrayList<>())
                    .add(permIdToKey.getOrDefault(row.getPermissionId(), ""));
        }
        return result;
    }

    @Transactional
    public void setUserPermissions(Long userId, List<String> permissionKeys) {
        userPermissionRepository.deleteByUserId(userId);
        if (permissionKeys == null || permissionKeys.isEmpty()) return;

        Map<String, Long> keyToId = permissionRepository.findAllByOrderByDisplayOrder().stream()
                .collect(Collectors.toMap(PermissionEntity::getPermissionKey, PermissionEntity::getId));

        for (String key : permissionKeys) {
            Long permId = keyToId.get(key);
            if (permId == null) continue;
            UserPermissionEntity e = new UserPermissionEntity();
            e.setUserId(userId);
            e.setPermissionId(permId);
            userPermissionRepository.save(e);
        }
    }

    @Transactional
    public void setGroupPermissions(Long groupId, List<String> permissionKeys) {
        groupPermissionRepository.deleteByGroupId(groupId);
        if (permissionKeys == null || permissionKeys.isEmpty()) return;

        Map<String, Long> keyToId = permissionRepository.findAllByOrderByDisplayOrder().stream()
                .collect(Collectors.toMap(PermissionEntity::getPermissionKey, PermissionEntity::getId));

        for (String key : permissionKeys) {
            Long permId = keyToId.get(key);
            if (permId == null) continue;
            GroupPermissionEntity e = new GroupPermissionEntity();
            e.setGroupId(groupId);
            e.setPermissionId(permId);
            groupPermissionRepository.save(e);
        }
    }

    private PermissionDto toDto(PermissionEntity e) {
        PermissionDto dto = new PermissionDto();
        dto.setId(e.getId());
        dto.setPermissionKey(e.getPermissionKey());
        dto.setDisplayOrder(e.getDisplayOrder());
        return dto;
    }
}
