package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.CreateGroupDto;
import com.turbotikects.turbotikectsserver.dto.GroupListItemDto;
import com.turbotikects.turbotikectsserver.dto.UpdateGroupDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class GroupManagementService {

    private final GroupRepository groupRepository;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final TaskProgressService taskProgressService;

    public GroupManagementService(GroupRepository groupRepository,
                                  FieldDefinitionsRepository fieldDefinitionsRepository,
                                  TaskProgressService taskProgressService) {
        this.groupRepository = groupRepository;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.taskProgressService = taskProgressService;
    }

    public List<GroupListItemDto> getAllGroups() {
        return groupRepository.findAll().stream().map(this::toDto).toList();
    }

    public GroupListItemDto createGroup(CreateGroupDto dto) {
        if (dto.getDisplayName() == null || dto.getDisplayName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");

        GroupEntity group = new GroupEntity();
        group.setDisplayName(dto.getDisplayName().trim());
        group.setMetadata(new HashMap<>());
        groupRepository.save(group);

        return toDto(group);
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
        return toDto(group);
    }

    public void deleteGroup(Long id) {
        GroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        groupRepository.delete(group);
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

    private GroupListItemDto toDto(GroupEntity group) {
        GroupListItemDto dto = new GroupListItemDto();
        dto.setId(group.getRefId());
        dto.setDisplayName(group.getDisplayName());
        dto.setMetadata(group.getMetadata());
        return dto;
    }
}
