package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.PatchWorkflowItemDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowItemDto;
import com.turbotikects.turbotikectsserver.entitys.GroupMemberEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import com.turbotikects.turbotikectsserver.entitys.WorkflowItemEntity;
import com.turbotikects.turbotikectsserver.repositorys.GroupMemberRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.repositorys.WorkflowItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkflowService {

    private final WorkflowItemRepository workflowItemRepo;
    private final TemplateVersionRepository versionRepo;
    private final UserRepository userRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final AdminInboxService adminInboxService;
    private final NotificationService notificationService;
    private final TicketRepository ticketRepo;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public WorkflowService(WorkflowItemRepository workflowItemRepo,
                           TemplateVersionRepository versionRepo,
                           UserRepository userRepo,
                           GroupMemberRepository groupMemberRepo,
                           @Autowired AdminInboxService adminInboxService,
                           @Autowired NotificationService notificationService,
                           @Autowired TicketRepository ticketRepo) {
        this.workflowItemRepo = workflowItemRepo;
        this.versionRepo = versionRepo;
        this.userRepo = userRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.adminInboxService = adminInboxService;
        this.notificationService = notificationService;
        this.ticketRepo = ticketRepo;
    }

    // ── SEED ─────────────────────────────────────────────────────────────────

    /**
     * Called when a ticket is created. Reads the workflow field from the template version
     * layout and creates a WorkflowItemEntity (status=pending) for each node.
     * Parent links are wired in a second pass so all items have DB IDs first.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void seedWorkflowItems(Long ticketId, Long templateVersionId) {
        Optional<TemplateVersionEntity> versionOpt = versionRepo.findById(templateVersionId);
        if (versionOpt.isEmpty()) return;

        List<Map<String, Object>> nodes = extractWorkflowNodes(versionOpt.get().getLayout());
        if (nodes.isEmpty()) return;

        // Pass 1: create all items with status=pending
        Map<String, WorkflowItemEntity> nodeIdToItem = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            if (nodeId == null) continue;

            WorkflowItemEntity item = new WorkflowItemEntity();
            item.setTicketId(ticketId);
            item.setTemplateNodeId(nodeId);
            item.setTitle(node.containsKey("title") ? (String) node.get("title") : "Workflow Item");
            item.setStatus("pending");
            item.setDisplayOrder(node.containsKey("displayOrder")
                    ? ((Number) node.get("displayOrder")).intValue() : 0);
            if (node.get("defaultAssigneeUserId") != null) {
                item.setAssignedUserId(((Number) node.get("defaultAssigneeUserId")).intValue());
            }
            if (node.get("defaultAssigneeGroupId") != null) {
                item.setAssignedGroupId(((Number) node.get("defaultAssigneeGroupId")).intValue());
            }
            item = workflowItemRepo.save(item);
            nodeIdToItem.put(nodeId, item);
        }

        // Pass 2: wire parent links
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            String parentId = (String) node.get("parentId");
            if (nodeId == null || parentId == null) continue;
            WorkflowItemEntity child = nodeIdToItem.get(nodeId);
            WorkflowItemEntity parent = nodeIdToItem.get(parentId);
            if (child != null && parent != null) {
                child.setParentItemId(parent.getId());
                workflowItemRepo.save(child);
            }
        }

        log.info("[Workflow] Seeded {} items for ticket {}", nodeIdToItem.size(), ticketId);
    }

    // ── CLONE ────────────────────────────────────────────────────────────────

    /**
     * Copies every workflow item from sourceTicketId onto newTicketId as an exact snapshot —
     * same status/assignee/field values, not re-seeded from the template. Two-pass like
     * seedWorkflowItems: items are saved first to get DB-generated ids, then parentItemId
     * references are remapped old-id -> new-id in a second pass.
     */
    @Transactional
    public void cloneWorkflowItems(Long sourceTicketId, Long newTicketId) {
        List<WorkflowItemEntity> sourceItems = workflowItemRepo.findByTicketIdOrderByDisplayOrder(sourceTicketId);
        if (sourceItems.isEmpty()) return;

        Map<Long, Long> oldToNewId = new HashMap<>();
        List<WorkflowItemEntity> newItems = new ArrayList<>();
        for (WorkflowItemEntity src : sourceItems) {
            WorkflowItemEntity copy = new WorkflowItemEntity();
            copy.setTicketId(newTicketId);
            copy.setTemplateNodeId(src.getTemplateNodeId());
            copy.setTitle(src.getTitle());
            copy.setStatus(src.getStatus());
            copy.setAssignedUserId(src.getAssignedUserId());
            copy.setAssignedGroupId(src.getAssignedGroupId());
            copy.setFieldValues(src.getFieldValues() != null ? new LinkedHashMap<>(src.getFieldValues()) : null);
            copy.setDisplayOrder(src.getDisplayOrder());
            copy = workflowItemRepo.save(copy);
            newItems.add(copy);
            oldToNewId.put(src.getId(), copy.getId());
        }
        for (int i = 0; i < sourceItems.size(); i++) {
            Long oldParentId = sourceItems.get(i).getParentItemId();
            if (oldParentId != null) {
                WorkflowItemEntity item = newItems.get(i);
                item.setParentItemId(oldToNewId.get(oldParentId));
                workflowItemRepo.save(item);
            }
        }

        log.info("[Workflow] Cloned {} items from ticket {} to ticket {}", newItems.size(), sourceTicketId, newTicketId);
    }

    // ── STATUS CASCADE ────────────────────────────────────────────────────────

    /**
     * Called whenever a ticket's status changes. Cascades to workflow items:
     * - ticket → open:               pending root items → in_progress + suspended → in_progress + canceled → in_progress
     * - ticket → closed/resolved:    in_progress → canceled
     * - ticket → waiting:            in_progress → suspended
     * - ticket → new/in_progress:    suspended → in_progress, canceled → in_progress
     */
    @Transactional
    public void cascadeTicketStatus(Long ticketId, String newStatus) {
        List<WorkflowItemEntity> items = workflowItemRepo.findByTicketIdOrderByDisplayOrder(ticketId);
        if (items.isEmpty()) return;

        List<WorkflowItemEntity> toSave = new ArrayList<>();
        List<WorkflowItemEntity> toNotify = new ArrayList<>();

        if ("open".equals(newStatus)) {
            for (WorkflowItemEntity item : items) {
                if (item.getParentItemId() == null && "pending".equals(item.getStatus())) {
                    item.setStatus("in_progress");
                    toSave.add(item);
                    toNotify.add(item);
                }
            }
            // Resume items paused by a previous suspend/close
            for (WorkflowItemEntity item : items) {
                if ("suspended".equals(item.getStatus()) || "canceled".equals(item.getStatus())) {
                    item.setStatus("in_progress");
                    toSave.add(item);
                    toNotify.add(item);
                }
            }
        } else if ("new".equals(newStatus) || "in_progress".equals(newStatus)) {
            for (WorkflowItemEntity item : items) {
                if ("suspended".equals(item.getStatus()) || "canceled".equals(item.getStatus())) {
                    item.setStatus("in_progress");
                    toSave.add(item);
                    toNotify.add(item);
                }
            }
        } else if ("closed".equals(newStatus) || "resolved".equals(newStatus)) {
            for (WorkflowItemEntity item : items) {
                if ("in_progress".equals(item.getStatus())) {
                    item.setStatus("canceled");
                    toSave.add(item);
                }
            }
        } else if ("waiting".equals(newStatus)) {
            for (WorkflowItemEntity item : items) {
                if ("in_progress".equals(item.getStatus())) {
                    item.setStatus("suspended");
                    toSave.add(item);
                }
            }
        }

        if (!toSave.isEmpty()) {
            workflowItemRepo.saveAll(toSave);
            log.info("[Workflow] Cascaded ticket {} status={} → updated {} items", ticketId, newStatus, toSave.size());
        }

        for (WorkflowItemEntity item : toNotify) {
            notifyAssigneeAsync(item, ticketId);
        }
    }

    // ── ITEM COMPLETION ───────────────────────────────────────────────────────

    /**
     * Called when a workflow item is marked done. Activates any pending children
     * and notifies their assignees.
     */
    @Transactional
    public void onItemCompleted(Long completedItemId, Long ticketId) {
        List<WorkflowItemEntity> children = workflowItemRepo.findByParentItemId(completedItemId);
        for (WorkflowItemEntity child : children) {
            if ("pending".equals(child.getStatus())) {
                child.setStatus("in_progress");
                workflowItemRepo.save(child);
                notifyAssigneeAsync(child, ticketId);
                log.info("[Workflow] Activated child item {} after parent {} completed", child.getId(), completedItemId);
            }
        }
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    public List<WorkflowItemDto> getItems(Long ticketId) {
        List<WorkflowItemEntity> items = workflowItemRepo.findByTicketIdOrderByDisplayOrder(ticketId);
        return toDtos(items);
    }

    public List<WorkflowItemDto> getItemsForUser(Integer userId) {
        if (userId == null) return Collections.emptyList();

        // Items directly assigned to the user
        List<WorkflowItemEntity> items = new ArrayList<>(workflowItemRepo.findByAssignedUserId(userId));

        // Items assigned to any group the user belongs to
        List<GroupMemberEntity> memberships = groupMemberRepo.findByUserId(userId.longValue());
        if (!memberships.isEmpty()) {
            List<Integer> groupIds = memberships.stream()
                    .map(m -> m.getGroupId().intValue())
                    .collect(Collectors.toList());
            List<WorkflowItemEntity> groupItems = workflowItemRepo.findByAssignedGroupIdIn(groupIds);
            // Deduplicate by item ID
            Set<Long> seen = items.stream().map(WorkflowItemEntity::getId).collect(Collectors.toSet());
            for (WorkflowItemEntity gi : groupItems) {
                if (seen.add(gi.getId())) items.add(gi);
            }
        }

        items.sort(Comparator.comparingInt(WorkflowItemEntity::getDisplayOrder));
        return toDtos(items);
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Transactional
    public WorkflowItemDto patchItem(Long id, PatchWorkflowItemDto dto, Integer actorId) {
        WorkflowItemEntity item = workflowItemRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String oldStatus = item.getStatus();

        if (dto.getStatus() != null) item.setStatus(dto.getStatus());
        if (dto.getAssignedUserId() != null) item.setAssignedUserId(dto.getAssignedUserId());
        if (dto.getAssignedGroupId() != null) item.setAssignedGroupId(dto.getAssignedGroupId());
        if (dto.getFieldValues() != null) item.setFieldValues(dto.getFieldValues());

        item = workflowItemRepo.save(item);

        if ("done".equals(item.getStatus()) && !"done".equals(oldStatus)) {
            onItemCompleted(item.getId(), item.getTicketId());
        }

        return toDto(item, loadDisplayNames(List.of(item)));
    }

    @Transactional
    public WorkflowItemDto patchItemStatus(Long id, String status, Integer actorId) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        WorkflowItemEntity item = workflowItemRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String oldStatus = item.getStatus();
        item.setStatus(status);
        item = workflowItemRepo.save(item);

        if ("done".equals(status) && !"done".equals(oldStatus)) {
            onItemCompleted(item.getId(), item.getTicketId());
        }

        return toDto(item, loadDisplayNames(List.of(item)));
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractWorkflowNodes(Map<String, Object> layout) {
        if (layout == null) return Collections.emptyList();
        Object tabsObj = layout.get("tabs");
        if (!(tabsObj instanceof List<?>)) return Collections.emptyList();

        for (Object tabObj : (List<?>) tabsObj) {
            if (!(tabObj instanceof Map)) continue;
            Map<String, Object> tab = (Map<String, Object>) tabObj;
            Object fieldsObj = tab.get("fields");
            if (!(fieldsObj instanceof List<?>)) continue;

            for (Object fieldObj : (List<?>) fieldsObj) {
                if (!(fieldObj instanceof Map)) continue;
                Map<String, Object> field = (Map<String, Object>) fieldObj;
                if (!"workflow".equals(field.get("fieldType"))) continue;
                Object fcObj = field.get("fieldConfig");
                if (!(fcObj instanceof Map)) continue;
                Object nodesObj = ((Map<?, ?>) fcObj).get("nodes");
                if (nodesObj instanceof List<?>) {
                    return (List<Map<String, Object>>) nodesObj;
                }
            }
        }
        return Collections.emptyList();
    }

    private void notifyAssigneeAsync(WorkflowItemEntity item, Long ticketId) {
        if (item.getAssignedUserId() == null && item.getAssignedGroupId() == null) return;

        // Snapshot values needed in the background thread (avoid detached entity access)
        Long    itemId          = item.getId();
        String  itemTitle       = item.getTitle();
        Integer assignedUserId  = item.getAssignedUserId();
        Integer assignedGroupId = item.getAssignedGroupId();

        new Thread(() -> {
            try {
                // Global in-app admin inbox entry (deduped per item)
                adminInboxService.createIfAbsent(ticketId, "wf_" + itemId, "WORKFLOW_ACTIVATED",
                        "Workflow item activated: " + itemTitle + " [TT-" + ticketId + "]");

                // Email notifications per recipient
                TicketEntity ticket = ticketRepo.findById(ticketId).orElse(null);
                if (ticket == null) return;

                WorkflowItemEntity freshItem = workflowItemRepo.findById(itemId).orElse(null);
                if (freshItem == null) return;

                List<Long> recipientIds = new ArrayList<>();
                if (assignedUserId != null) {
                    recipientIds.add(assignedUserId.longValue());
                }
                if (assignedGroupId != null) {
                    groupMemberRepo.findByGroupId(assignedGroupId.longValue()).forEach(m -> {
                        if (!recipientIds.contains(m.getUserId())) {
                            recipientIds.add(m.getUserId());
                        }
                    });
                }

                for (Long recipientId : recipientIds) {
                    notificationService.sendWorkflowItemNotification(freshItem, ticket, recipientId);
                }
            } catch (Exception e) {
                log.error("[Workflow] Notification error for item {}: {}", itemId, e.getMessage(), e);
            }
        }).start();
    }

    private List<WorkflowItemDto> toDtos(List<WorkflowItemEntity> items) {
        Map<Long, String> names = loadDisplayNames(items);
        return items.stream().map(i -> toDto(i, names)).collect(Collectors.toList());
    }

    private Map<Long, String> loadDisplayNames(List<WorkflowItemEntity> items) {
        Set<Long> ids = items.stream()
                .filter(i -> i.getAssignedUserId() != null)
                .map(i -> i.getAssignedUserId().longValue())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Long, String> names = new HashMap<>();
        userRepo.findAllById(new ArrayList<>(ids))
                .forEach(u -> names.put(u.getRed_id(),
                        u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        return names;
    }

    private WorkflowItemDto toDto(WorkflowItemEntity e, Map<Long, String> displayNames) {
        WorkflowItemDto dto = new WorkflowItemDto();
        dto.setId(e.getId());
        dto.setTicketId(e.getTicketId());
        dto.setTemplateNodeId(e.getTemplateNodeId());
        dto.setParentItemId(e.getParentItemId());
        dto.setTitle(e.getTitle());
        dto.setStatus(e.getStatus());
        dto.setAssignedUserId(e.getAssignedUserId());
        if (e.getAssignedUserId() != null) {
            dto.setAssignedUserDisplayName(displayNames.get(e.getAssignedUserId().longValue()));
        }
        dto.setAssignedGroupId(e.getAssignedGroupId());
        dto.setFieldValues(e.getFieldValues());
        dto.setDisplayOrder(e.getDisplayOrder());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }
}
