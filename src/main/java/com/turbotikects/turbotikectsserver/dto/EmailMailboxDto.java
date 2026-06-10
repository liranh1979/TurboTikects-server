package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailMailboxDto {
    private Long id;
    private String displayName;
    private String emailAddress;
    private String protocolType;

    // IMAP/SMTP
    private String imapHost;
    private Integer imapPort;
    private Boolean imapSsl;
    private String smtpHost;
    private Integer smtpPort;
    private Boolean smtpSsl;
    private String username;
    // password never returned in GET responses — write-only
    private String password;

    // OAuth2
    private String oauth2ClientId;
    // client secret never returned
    private String oauth2ClientSecret;
    private String oauth2TenantId;
    private Boolean oauth2Authorized;

    // Status
    private Boolean canReceive;
    private Boolean canSend;
    private Boolean isDefaultSender;
    private Boolean isActive;

    // Processing
    private Integer pollIntervalSeconds;
    private Boolean aiEnabled;
    private Integer aiConfidenceThreshold;
    private String unknownSenderAction;
    private Boolean ignoreSignature;

    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
}
