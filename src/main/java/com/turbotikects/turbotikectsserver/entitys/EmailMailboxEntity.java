package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_mailboxes")
@Data
public class EmailMailboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    @Column(name = "protocol_type", nullable = false, length = 24)
    private String protocolType;

    // IMAP/SMTP fields
    @Column(name = "imap_host")
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_ssl")
    private Boolean imapSsl = true;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_ssl")
    private Boolean smtpSsl = true;

    @Column(name = "username")
    private String username;

    @Column(name = "password_encrypted", columnDefinition = "TEXT")
    private String passwordEncrypted;

    // OAuth2 fields
    @Column(name = "oauth2_client_id")
    private String oauth2ClientId;

    @Column(name = "oauth2_client_secret_enc", columnDefinition = "TEXT")
    private String oauth2ClientSecretEnc;

    @Column(name = "oauth2_tenant_id")
    private String oauth2TenantId;

    @Column(name = "oauth2_access_token_enc", columnDefinition = "TEXT")
    private String oauth2AccessTokenEnc;

    @Column(name = "oauth2_refresh_token_enc", columnDefinition = "TEXT")
    private String oauth2RefreshTokenEnc;

    @Column(name = "oauth2_token_expiry")
    private LocalDateTime oauth2TokenExpiry;

    // Status flags
    @Column(name = "can_receive")
    private Boolean canReceive = true;

    @Column(name = "can_send")
    private Boolean canSend = true;

    @Column(name = "is_default_sender")
    private Boolean isDefaultSender = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Processing settings
    @Column(name = "poll_interval_seconds")
    private Integer pollIntervalSeconds = 300;

    @Column(name = "ai_enabled")
    private Boolean aiEnabled = false;

    @Column(name = "ai_confidence_threshold")
    private Integer aiConfidenceThreshold = 60;

    @Column(name = "unknown_sender_action", length = 16)
    private String unknownSenderAction = "ignore";

    @Column(name = "ignore_signature")
    private Boolean ignoreSignature = true;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

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
