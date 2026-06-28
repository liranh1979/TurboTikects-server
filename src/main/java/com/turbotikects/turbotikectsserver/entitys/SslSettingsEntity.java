package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ssl_settings")
@Data
public class SslSettingsEntity {

    @Id
    private Integer id = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "cert_type", length = 20)
    private String certType;

    @Column(name = "domain", length = 500)
    private String domain;

    @Column(name = "keystore_path", length = 500)
    private String keystorePath;

    @Column(name = "keystore_password", length = 200)
    private String keystorePassword;

    @Column(name = "https_port", nullable = false)
    private int httpsPort = 3443;

    @Column(name = "cert_subject", length = 500)
    private String certSubject;

    @Column(name = "cert_issuer", length = 500)
    private String certIssuer;

    @Column(name = "cert_expiry")
    private LocalDateTime certExpiry;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
