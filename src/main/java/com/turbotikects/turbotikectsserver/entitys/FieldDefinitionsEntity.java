package com.turbotikects.turbotikectsserver.entitys;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

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

    @JsonProperty("isListVisible")
    @Column(name = "is_list_visible")
    private boolean isListVisible;

    @JsonProperty("isDetailVisible")
    @Column(name = "is_detail_visible")
    private boolean isDetailVisible;

    @Column(name = "display_order")
    private int displayOrder;

    @JsonProperty("isSystem")
    @Column(name = "is_system")
    private boolean isSystem;

    @JsonProperty("isAdminOnly")
    @Column(name = "is_admin_only")
    private boolean isAdminOnly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_options", columnDefinition = "json")
    private List<String> fieldOptions;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}