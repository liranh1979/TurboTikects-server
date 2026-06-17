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

    @Column(name = "assigned_user_id")
    private Integer assignedUserId;

    @Column(name = "assigned_group_id")
    private Integer assignedGroupId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_values", columnDefinition = "json")
    private Map<String, Object> fieldValues;

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
