package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "field_definitions")
@Data
public class FieldDefinitionsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_type", nullable = false)
    private String fieldType;

    @Column(name = "is_list_visible")
    private boolean isListVisible;

    @Column(name = "is_detail_visible")
    private boolean isDetailVisible;

    @Column(name = "display_order")
    private int displayOrder;

    @Column(name = "is_system")
    private boolean isSystem;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}