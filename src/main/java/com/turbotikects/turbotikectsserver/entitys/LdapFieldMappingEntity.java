package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ldap_field_mappings")
@Data
public class LdapFieldMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ldap_config_id", nullable = false)
    private Long ldapConfigId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "ldap_attribute", nullable = false)
    private String ldapAttribute;

    @Column(name = "system_field_key", nullable = false)
    private String systemFieldKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
