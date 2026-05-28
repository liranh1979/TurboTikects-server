package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.BulkGroupMembershipDto;
import com.turbotikects.turbotikectsserver.dto.CreateGroupDto;
import com.turbotikects.turbotikectsserver.dto.GroupListItemDto;
import com.turbotikects.turbotikectsserver.dto.GroupMemberDto;
import com.turbotikects.turbotikectsserver.dto.UpdateGroupDto;
import com.turbotikects.turbotikectsserver.services.GroupManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "${app.cors.origins}")
@RestController
@RequestMapping("/api/v1/groups")
public class GroupManagementController {

    private final GroupManagementService groupManagementService;

    public GroupManagementController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    @GetMapping
    public List<GroupListItemDto> getAllGroups() {
        return groupManagementService.getAllGroups();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupListItemDto createGroup(@RequestBody CreateGroupDto dto) {
        return groupManagementService.createGroup(dto);
    }

    @PatchMapping("/{id}")
    public GroupListItemDto updateGroup(@PathVariable Long id, @RequestBody UpdateGroupDto dto) {
        return groupManagementService.updateGroup(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long id) {
        groupManagementService.deleteGroup(id);
    }

    @PostMapping("/sync-metadata")
    public Map<String, String> syncMetadata() {
        String taskId = groupManagementService.startSyncMetadata();
        return Map.of("taskId", taskId);
    }

    @GetMapping("/{id}/members")
    public List<GroupMemberDto> getMembers(@PathVariable Long id) {
        return groupManagementService.getGroupMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMembers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        groupManagementService.addMembers(id, userIds);
    }

    @DeleteMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMembers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        groupManagementService.removeMembers(id, userIds);
    }

    @GetMapping("/by-user/{userId}")
    public List<GroupListItemDto> getGroupsForUser(@PathVariable Long userId) {
        return groupManagementService.getGroupsForUser(userId);
    }

    @PostMapping("/bulk-add")
    public Map<String, String> bulkAddToGroups(@RequestBody BulkGroupMembershipDto dto) {
        String taskId = groupManagementService.bulkAddToGroups(dto);
        return Map.of("taskId", taskId);
    }

    @PostMapping("/bulk-remove")
    public Map<String, String> bulkRemoveFromGroups(@RequestBody BulkGroupMembershipDto dto) {
        String taskId = groupManagementService.bulkRemoveFromGroups(dto);
        return Map.of("taskId", taskId);
    }
}
