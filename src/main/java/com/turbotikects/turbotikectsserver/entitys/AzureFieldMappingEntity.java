package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "azure_field_mappings")
@Data
public class AzureFieldMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "azure_config_id", nullable = false)
    private Long azureConfigId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "azure_attribute", nullable = false)
    private String azureAttribute;

    @Column(name = "system_field_key", nullable = false)
    private String systemFieldKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
