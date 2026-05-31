package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ldap_configs")
@Data
public class LdapConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private int port = 389;

    @Column(name = "use_ssl", nullable = false)
    private boolean useSsl = false;

    @Column(name = "bind_dn", nullable = false)
    private String bindDn;

    @Column(name = "bind_password", nullable = false, columnDefinition = "TEXT")
    private String bindPassword;

    @Column(name = "base_dn_users", nullable = false)
    private String baseDnUsers;

    @Column(name = "base_dn_groups")
    private String baseDnGroups;

    @Column(name = "user_filter", nullable = false)
    private String userFilter = "(objectClass=person)";

    @Column(name = "group_filter", nullable = false)
    private String groupFilter = "(objectClass=group)";

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
