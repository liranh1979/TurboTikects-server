package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.BulkGroupMembershipDto;
import com.turbotikects.turbotikectsserver.dto.CreateGroupDto;
import com.turbotikects.turbotikectsserver.dto.GroupListItemDto;
import com.turbotikects.turbotikectsserver.dto.GroupMemberDto;
import com.turbotikects.turbotikectsserver.dto.UpdateGroupDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import com.turbotikects.turbotikectsserver.entitys.GroupMemberEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupMemberRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GroupManagementService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final TaskProgressService taskProgressService;
    private final PermissionService permissionService;

    public GroupManagementService(GroupRepository groupRepository,
                                  GroupMemberRepository groupMemberRepository,
                                  UserRepository userRepository,
                                  FieldDefinitionsRepository fieldDefinitionsRepository,
                                  TaskProgressService taskProgressService,
                                  PermissionService permissionService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.taskProgressService = taskProgressService;
        this.permissionService = permissionService;
    }

    public List<GroupListItemDto> getAllGroups() {
        List<GroupEntity> groups = groupRepository.findAll();

        Map<Long, Long> memberCounts = groupMemberRepository.findAll().stream()
                .collect(Collectors.groupingBy(GroupMemberEntity::getGroupId, Collectors.counting()));

        List<Long> groupIds = groups.stream().map(GroupEntity::getRefId).toList();
        Map<Long, List<String>> permMap = permissionService.getGroupPermissionsForGroups(groupIds);

        return groups.stream().map(g -> {
            GroupListItemDto dto = toDto(g, memberCounts.getOrDefault(g.getRefId(), 0L).intValue());
            dto.setPermissions(permMap.getOrDefault(g.getRefId(), List.of()));
            return dto;
        }).toList();
    }

    public GroupListItemDto createGroup(CreateGroupDto dto) {
        if (dto.getDisplayName() == null || dto.getDisplayName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");

        GroupEntity group = new GroupEntity();
        group.setDisplayName(dto.getDisplayName().trim());
        group.setMetadata(new HashMap<>());
        groupRepository.save(group);

        return toDto(group, 0);
    }

    public GroupListItemDto updateGroup(Long id, UpdateGroupDto dto) {
        GroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank())
            group.setDisplayName(dto.getDisplayName().trim());

        if (dto.getMetadata() != null) {
            Map<String, Object> merged = group.getMetadata() != null
                    ? new HashMap<>(group.getMetadata()) : new HashMap<>();
            merged.putAll(dto.getMetadata());
            group.setMetadata(merged);
        }

        groupRepository.save(group);

        if (dto.getPermissions() != null) {
            permissionService.setGroupPermissions(id, dto.getPermissions());
        }

        int count = (int) groupMemberRepository.countByGroupId(id);
        GroupListItemDto result = toDto(group, count);
        result.setPermissions(permissionService.getGroupPermissions(id));
        return result;
    }

    public void deleteGroup(Long id) {
        GroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        groupRepository.delete(group);
    }

    public List<GroupMemberDto> getGroupMembers(Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        List<Long> userIds = groupMemberRepository.findByGroupId(groupId)
                .stream().map(GroupMemberEntity::getUserId).toList();

        return userRepository.findAllById(userIds).stream().map(u -> {
            GroupMemberDto dto = new GroupMemberDto();
            dto.setUserId(u.getRed_id());
            dto.setUsername(u.getUsername());
            dto.setDisplayName(u.getDisplayName());
            return dto;
        }).toList();
    }

    public void addMembers(Long groupId, List<Long> userIds) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        for (Long userId : userIds) {
            if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
                GroupMemberEntity m = new GroupMemberEntity();
                m.setGroupId(groupId);
                m.setUserId(userId);
                groupMemberRepository.save(m);
            }
        }
    }

    @Transactional
    public void removeMembers(Long groupId, List<Long> userIds) {
        groupMemberRepository.deleteByGroupIdAndUserIdIn(groupId, userIds);
    }

    public List<GroupListItemDto> getGroupsForUser(Long userId) {
        List<Long> groupIds = groupMemberRepository.findByUserId(userId)
                .stream().map(GroupMemberEntity::getGroupId).toList();
        return groupRepository.findAllById(groupIds).stream()
                .map(g -> toDto(g, 0))
                .toList();
    }

    public String bulkAddToGroups(BulkGroupMembershipDto dto) {
        List<Long> userIds  = dto.getUserIds();
        List<Long> groupIds = dto.getGroupIds();
        int total = groupIds.size();
        String taskId = taskProgressService.createTask("Bulk Add to Groups", total);

        new Thread(() -> {
            try {
                for (int gi = 0; gi < groupIds.size(); gi++) {
                    Long groupId = groupIds.get(gi);
                    for (Long userId : userIds) {
                        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
                            GroupMemberEntity m = new GroupMemberEntity();
                            m.setGroupId(groupId);
                            m.setUserId(userId);
                            groupMemberRepository.save(m);
                        }
                    }
                    taskProgressService.updateProgress(taskId, gi + 1,
                            "Processed group " + (gi + 1) + " of " + groupIds.size());
                }
                taskProgressService.completeTask(taskId,
                        "Added " + userIds.size() + " user(s) to " + groupIds.size() + " group(s)");
            } catch (Exception e) {
                taskProgressService.failTask(taskId, "Error: " + e.getMessage());
            }
        }).start();

        return taskId;
    }

    public String bulkRemoveFromGroups(BulkGroupMembershipDto dto) {
        List<Long> userIds  = dto.getUserIds();
        List<Long> groupIds = dto.getGroupIds();
        int total = groupIds.size();
        String taskId = taskProgressService.createTask("Bulk Remove from Groups", total);

        new Thread(() -> {
            try {
                for (int gi = 0; gi < groupIds.size(); gi++) {
                    groupMemberRepository.deleteByGroupIdAndUserIdIn(groupIds.get(gi), userIds);
                    taskProgressService.updateProgress(taskId, gi + 1,
                            "Processed group " + (gi + 1) + " of " + groupIds.size());
                }
                taskProgressService.completeTask(taskId,
                        "Removed " + userIds.size() + " user(s) from " + groupIds.size() + " group(s)");
            } catch (Exception e) {
                taskProgressService.failTask(taskId, "Error: " + e.getMessage());
            }
        }).start();

        return taskId;
    }

    public String startSyncMetadata() {
        List<GroupEntity> groups = groupRepository.findAll();
        List<FieldDefinitionsEntity> fields =
                fieldDefinitionsRepository.findByEntityTypeAndIsSystemFalseOrderByDisplayOrder("group");

        String taskId = taskProgressService.createTask("Sync Group Metadata", groups.size());

        new Thread(() -> {
            try {
                for (int i = 0; i < groups.size(); i++) {
                    GroupEntity group = groups.get(i);
                    Map<String, Object> metadata = group.getMetadata() != null
                            ? new HashMap<>(group.getMetadata())
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
                        group.setMetadata(metadata);
                        groupRepository.save(group);
                    }

                    taskProgressService.updateProgress(taskId, i + 1,
                            "Processing group: " + group.getDisplayName());

                    Thread.sleep(100);
                }
                taskProgressService.completeTask(taskId,
                        "Synced metadata for " + groups.size() + " groups");
            } catch (Exception e) {
                taskProgressService.failTask(taskId, "Error: " + e.getMessage());
            }
        }).start();

        return taskId;
    }

    private GroupListItemDto toDto(GroupEntity group, int memberCount) {
        GroupListItemDto dto = new GroupListItemDto();
        dto.setId(group.getRefId());
        dto.setDisplayName(group.getDisplayName());
        dto.setMetadata(group.getMetadata());
        dto.setMemberCount(memberCount);
        return dto;
    }
}
