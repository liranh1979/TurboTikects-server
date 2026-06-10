package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.EmailConnectionTestResultDto;
import com.turbotikects.turbotikectsserver.dto.EmailFilterDto;
import com.turbotikects.turbotikectsserver.dto.EmailMailboxDto;
import com.turbotikects.turbotikectsserver.entitys.EmailFilterEntity;
import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import com.turbotikects.turbotikectsserver.repositorys.EmailFilterRepository;
import com.turbotikects.turbotikectsserver.repositorys.EmailMailboxRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailMailboxService {

    @Autowired
    private EmailMailboxRepository mailboxRepo;

    @Autowired
    private EmailFilterRepository filterRepo;

    @Autowired
    private AesEncryptionUtils aesEncryptionUtils;

    // ── CRUD ────────────────────────────────────────────────────────────────────

    public List<EmailMailboxDto> getAllMailboxes() {
        return mailboxRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public EmailMailboxDto createMailbox(EmailMailboxDto dto) {
        EmailMailboxEntity entity = new EmailMailboxEntity();
        applyDtoToEntity(dto, entity, true);
        entity = mailboxRepo.save(entity);
        return toDto(entity);
    }

    public EmailMailboxDto updateMailbox(Long id, EmailMailboxDto dto) {
        EmailMailboxEntity entity = mailboxRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyDtoToEntity(dto, entity, false);
        entity = mailboxRepo.save(entity);
        return toDto(entity);
    }

    public void deleteMailbox(Long id) {
        if (!mailboxRepo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        mailboxRepo.deleteById(id);
    }

    public void setDefaultSender(Long id) {
        if (!mailboxRepo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        mailboxRepo.clearAllDefaults();
        EmailMailboxEntity entity = mailboxRepo.findById(id).orElseThrow();
        entity.setIsDefaultSender(true);
        mailboxRepo.save(entity);
    }

    // ── FILTERS ─────────────────────────────────────────────────────────────────

    public List<EmailFilterDto> getFilters(Long mailboxId) {
        return filterRepo.findByMailboxId(mailboxId).stream().map(this::toFilterDto).collect(Collectors.toList());
    }

    public EmailFilterDto addFilter(Long mailboxId, EmailFilterDto dto) {
        if (!mailboxRepo.existsById(mailboxId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        EmailFilterEntity entity = new EmailFilterEntity();
        entity.setMailboxId(mailboxId);
        entity.setListType(dto.getListType());
        entity.setEmailPattern(dto.getEmailPattern());
        entity = filterRepo.save(entity);
        return toFilterDto(entity);
    }

    public void deleteFilter(Long mailboxId, Long filterId) {
        EmailFilterEntity filter = filterRepo.findById(filterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!mailboxId.equals(filter.getMailboxId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        filterRepo.deleteById(filterId);
    }

    // ── CONNECTION TEST ──────────────────────────────────────────────────────────

    public EmailConnectionTestResultDto testConnection(Long id) {
        EmailMailboxEntity mailbox = mailboxRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            return switch (mailbox.getProtocolType()) {
                case "IMAP_SMTP" -> testImap(mailbox);
                case "OAUTH2_GMAIL" -> testGmail(mailbox);
                case "OAUTH2_MICROSOFT" -> testMicrosoft(mailbox);
                default -> new EmailConnectionTestResultDto(false, "Unknown protocol");
            };
        } catch (Exception e) {
            log.error("Connection test failed for mailbox {}: {}", id, e.getMessage());
            return new EmailConnectionTestResultDto(false, e.getMessage());
        }
    }

    private EmailConnectionTestResultDto testImap(EmailMailboxEntity mailbox) {
        try {
            java.util.Properties props = new java.util.Properties();
            String protocol = Boolean.TRUE.equals(mailbox.getImapSsl()) ? "imaps" : "imap";
            props.put("mail.store.protocol", protocol);
            props.put("mail." + protocol + ".host", mailbox.getImapHost());
            props.put("mail." + protocol + ".port", String.valueOf(mailbox.getImapPort()));
            props.put("mail." + protocol + ".ssl.enable", Boolean.TRUE.equals(mailbox.getImapSsl()) ? "true" : "false");
            props.put("mail." + protocol + ".connectiontimeout", "5000");
            props.put("mail." + protocol + ".timeout", "5000");

            jakarta.mail.Session session = jakarta.mail.Session.getInstance(props);
            jakarta.mail.Store store = session.getStore(protocol);
            String password = mailbox.getPasswordEncrypted() != null
                    ? aesEncryptionUtils.decrypt(mailbox.getPasswordEncrypted()) : "";
            store.connect(mailbox.getImapHost(), mailbox.getUsername(), password);
            jakarta.mail.Folder inbox = store.getFolder("INBOX");
            inbox.open(jakarta.mail.Folder.READ_ONLY);
            inbox.close(false);
            store.close();
            return new EmailConnectionTestResultDto(true, "Connected");
        } catch (Exception e) {
            return new EmailConnectionTestResultDto(false, "IMAP error: " + e.getMessage());
        }
    }

    private EmailConnectionTestResultDto testGmail(EmailMailboxEntity mailbox) throws Exception {
        String token = getValidGmailToken(mailbox);
        if (token == null) return new EmailConnectionTestResultDto(false, "Not authorized — please complete OAuth2 flow");

        HttpRequest req = HttpRequest.newBuilder(new URI("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) return new EmailConnectionTestResultDto(true, "Connected");
        return new EmailConnectionTestResultDto(false, "Gmail API error " + res.statusCode());
    }

    private EmailConnectionTestResultDto testMicrosoft(EmailMailboxEntity mailbox) throws Exception {
        String token = getValidMicrosoftToken(mailbox);
        if (token == null) return new EmailConnectionTestResultDto(false, "Not authorized — please complete OAuth2 flow");

        HttpRequest req = HttpRequest.newBuilder(new URI("https://graph.microsoft.com/v1.0/me/mailFolders/inbox"))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) return new EmailConnectionTestResultDto(true, "Connected");
        return new EmailConnectionTestResultDto(false, "Graph API error " + res.statusCode());
    }

    // ── OAUTH2 ───────────────────────────────────────────────────────────────────

    public String buildGmailAuthUrl(Long mailboxId) {
        EmailMailboxEntity mailbox = mailboxRepo.findById(mailboxId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + mailbox.getOauth2ClientId()
                + "&redirect_uri=" + java.net.URLEncoder.encode(getRedirectUri("gmail"), java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + java.net.URLEncoder.encode("https://mail.google.com/", java.nio.charset.StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + mailboxId;
    }

    public String buildMicrosoftAuthUrl(Long mailboxId) {
        EmailMailboxEntity mailbox = mailboxRepo.findById(mailboxId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String tenant = mailbox.getOauth2TenantId() != null ? mailbox.getOauth2TenantId() : "common";
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize"
                + "?client_id=" + mailbox.getOauth2ClientId()
                + "&redirect_uri=" + java.net.URLEncoder.encode(getRedirectUri("microsoft"), java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + java.net.URLEncoder.encode("https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send offline_access", java.nio.charset.StandardCharsets.UTF_8)
                + "&state=" + mailboxId;
    }

    public void handleOAuth2Callback(Long mailboxId, String code, String provider) throws Exception {
        EmailMailboxEntity mailbox = mailboxRepo.findById(mailboxId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ObjectMapper mapper = new ObjectMapper();
        String clientSecret = mailbox.getOauth2ClientSecretEnc() != null
                ? aesEncryptionUtils.decrypt(mailbox.getOauth2ClientSecretEnc()) : "";

        String tokenUrl;
        String body;
        if ("gmail".equals(provider)) {
            tokenUrl = "https://oauth2.googleapis.com/token";
            body = "grant_type=authorization_code"
                    + "&code=" + code
                    + "&client_id=" + mailbox.getOauth2ClientId()
                    + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8)
                    + "&redirect_uri=" + java.net.URLEncoder.encode(getRedirectUri("gmail"), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            String tenant = mailbox.getOauth2TenantId() != null ? mailbox.getOauth2TenantId() : "common";
            tokenUrl = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token";
            body = "grant_type=authorization_code"
                    + "&code=" + code
                    + "&client_id=" + mailbox.getOauth2ClientId()
                    + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8)
                    + "&redirect_uri=" + java.net.URLEncoder.encode(getRedirectUri("microsoft"), java.nio.charset.StandardCharsets.UTF_8)
                    + "&scope=" + java.net.URLEncoder.encode("https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send offline_access", java.nio.charset.StandardCharsets.UTF_8);
        }

        HttpRequest req = HttpRequest.newBuilder(new URI(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Token exchange failed: " + res.body());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = mapper.readValue(res.body(), Map.class);
        storeTokens(mailbox, tokenData);
    }

    // package-visible for EmailPollingService
    String getValidGmailToken(EmailMailboxEntity mailbox) throws Exception {
        if (mailbox.getOauth2AccessTokenEnc() == null) return null;
        if (mailbox.getOauth2TokenExpiry() != null
                && mailbox.getOauth2TokenExpiry().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return aesEncryptionUtils.decrypt(mailbox.getOauth2AccessTokenEnc());
        }
        return refreshGmailToken(mailbox);
    }

    String getValidMicrosoftToken(EmailMailboxEntity mailbox) throws Exception {
        if (mailbox.getOauth2AccessTokenEnc() == null) return null;
        if (mailbox.getOauth2TokenExpiry() != null
                && mailbox.getOauth2TokenExpiry().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return aesEncryptionUtils.decrypt(mailbox.getOauth2AccessTokenEnc());
        }
        return refreshMicrosoftToken(mailbox);
    }

    private String refreshGmailToken(EmailMailboxEntity mailbox) throws Exception {
        if (mailbox.getOauth2RefreshTokenEnc() == null) return null;
        String refreshToken = aesEncryptionUtils.decrypt(mailbox.getOauth2RefreshTokenEnc());
        String clientSecret = mailbox.getOauth2ClientSecretEnc() != null
                ? aesEncryptionUtils.decrypt(mailbox.getOauth2ClientSecretEnc()) : "";

        String body = "grant_type=refresh_token"
                + "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8)
                + "&client_id=" + mailbox.getOauth2ClientId()
                + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(new URI("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) return null;

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = new ObjectMapper().readValue(res.body(), Map.class);
        storeTokens(mailbox, tokenData);
        return (String) tokenData.get("access_token");
    }

    private String refreshMicrosoftToken(EmailMailboxEntity mailbox) throws Exception {
        if (mailbox.getOauth2RefreshTokenEnc() == null) return null;
        String refreshToken = aesEncryptionUtils.decrypt(mailbox.getOauth2RefreshTokenEnc());
        String clientSecret = mailbox.getOauth2ClientSecretEnc() != null
                ? aesEncryptionUtils.decrypt(mailbox.getOauth2ClientSecretEnc()) : "";
        String tenant = mailbox.getOauth2TenantId() != null ? mailbox.getOauth2TenantId() : "common";

        String body = "grant_type=refresh_token"
                + "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8)
                + "&client_id=" + mailbox.getOauth2ClientId()
                + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8)
                + "&scope=" + java.net.URLEncoder.encode("https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send offline_access", java.nio.charset.StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(
                new URI("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) return null;

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = new ObjectMapper().readValue(res.body(), Map.class);
        storeTokens(mailbox, tokenData);
        return (String) tokenData.get("access_token");
    }

    private void storeTokens(EmailMailboxEntity mailbox, Map<String, Object> tokenData) {
        String accessToken = (String) tokenData.get("access_token");
        if (accessToken != null) {
            mailbox.setOauth2AccessTokenEnc(aesEncryptionUtils.encrypt(accessToken));
        }
        if (tokenData.containsKey("refresh_token")) {
            mailbox.setOauth2RefreshTokenEnc(aesEncryptionUtils.encrypt((String) tokenData.get("refresh_token")));
        }
        Object expiresIn = tokenData.get("expires_in");
        if (expiresIn != null) {
            long seconds = expiresIn instanceof Number ? ((Number) expiresIn).longValue() : Long.parseLong(expiresIn.toString());
            mailbox.setOauth2TokenExpiry(LocalDateTime.now().plusSeconds(seconds));
        }
        mailboxRepo.save(mailbox);
    }

    // ── MAPPING ──────────────────────────────────────────────────────────────────

    private EmailMailboxDto toDto(EmailMailboxEntity e) {
        EmailMailboxDto dto = new EmailMailboxDto();
        dto.setId(e.getId());
        dto.setDisplayName(e.getDisplayName());
        dto.setEmailAddress(e.getEmailAddress());
        dto.setProtocolType(e.getProtocolType());
        dto.setImapHost(e.getImapHost());
        dto.setImapPort(e.getImapPort());
        dto.setImapSsl(e.getImapSsl());
        dto.setSmtpHost(e.getSmtpHost());
        dto.setSmtpPort(e.getSmtpPort());
        dto.setSmtpSsl(e.getSmtpSsl());
        dto.setUsername(e.getUsername());
        // never return passwords / tokens
        dto.setOauth2ClientId(e.getOauth2ClientId());
        dto.setOauth2TenantId(e.getOauth2TenantId());
        dto.setOauth2Authorized(e.getOauth2AccessTokenEnc() != null);
        dto.setCanReceive(e.getCanReceive());
        dto.setCanSend(e.getCanSend());
        dto.setIsDefaultSender(e.getIsDefaultSender());
        dto.setIsActive(e.getIsActive());
        dto.setPollIntervalSeconds(e.getPollIntervalSeconds());
        dto.setAiEnabled(e.getAiEnabled());
        dto.setAiConfidenceThreshold(e.getAiConfidenceThreshold());
        dto.setUnknownSenderAction(e.getUnknownSenderAction());
        dto.setIgnoreSignature(e.getIgnoreSignature());
        dto.setLastCheckedAt(e.getLastCheckedAt());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private void applyDtoToEntity(EmailMailboxDto dto, EmailMailboxEntity entity, boolean isNew) {
        if (dto.getDisplayName() != null) entity.setDisplayName(dto.getDisplayName());
        if (dto.getEmailAddress() != null) entity.setEmailAddress(dto.getEmailAddress());
        if (dto.getProtocolType() != null) entity.setProtocolType(dto.getProtocolType());
        if (dto.getImapHost() != null) entity.setImapHost(dto.getImapHost());
        if (dto.getImapPort() != null) entity.setImapPort(dto.getImapPort());
        if (dto.getImapSsl() != null) entity.setImapSsl(dto.getImapSsl());
        if (dto.getSmtpHost() != null) entity.setSmtpHost(dto.getSmtpHost());
        if (dto.getSmtpPort() != null) entity.setSmtpPort(dto.getSmtpPort());
        if (dto.getSmtpSsl() != null) entity.setSmtpSsl(dto.getSmtpSsl());
        if (dto.getUsername() != null) entity.setUsername(dto.getUsername());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            entity.setPasswordEncrypted(aesEncryptionUtils.encrypt(dto.getPassword()));
        }
        if (dto.getOauth2ClientId() != null) entity.setOauth2ClientId(dto.getOauth2ClientId());
        if (dto.getOauth2ClientSecret() != null && !dto.getOauth2ClientSecret().isBlank()) {
            entity.setOauth2ClientSecretEnc(aesEncryptionUtils.encrypt(dto.getOauth2ClientSecret()));
        }
        if (dto.getOauth2TenantId() != null) entity.setOauth2TenantId(dto.getOauth2TenantId());
        if (dto.getCanReceive() != null) entity.setCanReceive(dto.getCanReceive());
        if (dto.getCanSend() != null) entity.setCanSend(dto.getCanSend());
        if (dto.getIsDefaultSender() != null) entity.setIsDefaultSender(dto.getIsDefaultSender());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
        if (dto.getPollIntervalSeconds() != null) entity.setPollIntervalSeconds(dto.getPollIntervalSeconds());
        if (dto.getAiEnabled() != null) entity.setAiEnabled(dto.getAiEnabled());
        if (dto.getAiConfidenceThreshold() != null) entity.setAiConfidenceThreshold(dto.getAiConfidenceThreshold());
        if (dto.getUnknownSenderAction() != null) entity.setUnknownSenderAction(dto.getUnknownSenderAction());
        if (dto.getIgnoreSignature() != null) entity.setIgnoreSignature(dto.getIgnoreSignature());
    }

    private EmailFilterDto toFilterDto(EmailFilterEntity e) {
        EmailFilterDto dto = new EmailFilterDto();
        dto.setId(e.getId());
        dto.setMailboxId(e.getMailboxId());
        dto.setListType(e.getListType());
        dto.setEmailPattern(e.getEmailPattern());
        return dto;
    }

    private String getRedirectUri(String provider) {
        return "http://localhost:3000/api/v1/email/oauth2/" + provider + "/callback";
    }
}
