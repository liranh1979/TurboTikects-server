package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "users")
@Data
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "red_id") // This matches your image
    private Long red_id;

    @Column(name = "user_name", unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "display_name")
    private String displayName;

    @Column(name ="is_super_admin", nullable = false)
    private boolean isSuperAdmin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column( name = "user_metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "source_type", nullable = false)
    private int sourceType = 0;

    @Column(name = "email")
    private String email;

    @Column(name = "ldap_external_id")
    private String ldapExternalId;

    @Column(name = "azure_external_id")
    private String azureExternalId;

}
