package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "group_permissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "permission_id"}))
@Data
public class GroupPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;
}
