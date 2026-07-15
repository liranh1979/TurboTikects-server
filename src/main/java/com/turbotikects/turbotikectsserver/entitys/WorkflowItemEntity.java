package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "workflow_items")
@Data
public class WorkflowItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "template_node_id", length = 64, nullable = false)
    private String templateNodeId;

    @Column(name = "parent_item_id")
    private Long parentItemId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false, length = 32)
    private String status;

    // task (default, today's plain checklist behavior) | approval | external_api | mcp_tool
    @Column(nullable = false, length = 32)
    private String type = "task";

    // Type-specific config (approval chain levels, or the API/MCP call sequence + field mappings) —
    // deliberately separate from field_values below, which stays the user-facing custom-field bag.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_config", columnDefinition = "json")
    private Map<String, Object> typeConfig;

    // Null = always activate when the parent completes (today's behavior, unchanged for every
    // existing template). 'approved'/'rejected' = only activate if the parent is an approval item
    // that resolved that way — the minimal extension needed for FEAT-06 Mock 1's branching diagram
    // (approved and rejected paths lead to different children), which the flat parent/child tree
    // couldn't express before this column existed.
    @Column(name = "activation_condition", length = 16)
    private String activationCondition;

    @Column(name = "assigned_user_id")
    private Integer assignedUserId;

    @Column(name = "assigned_group_id")
    private Integer assignedGroupId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_values", columnDefinition = "json")
    private Map<String, Object> fieldValues;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "display_order")
    private int displayOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
