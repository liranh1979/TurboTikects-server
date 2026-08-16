package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.EmailAttachment;
import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import com.turbotikects.turbotikectsserver.entitys.EmailMessageLogEntity;
import com.turbotikects.turbotikectsserver.repositorys.EmailMailboxRepository;
import com.turbotikects.turbotikectsserver.repositorys.EmailMessageLogRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Slf4j
@Service
public class EmailSenderService {

    @Autowired
    private EmailMailboxRepository mailboxRepo;

    @Autowired
    private EmailMessageLogRepository messageLogRepo;

    @Autowired
    private AesEncryptionUtils aesEncryptionUtils;

    @Autowired
    private EmailMailboxService emailMailboxService;

    public void sendReply(EmailMailboxEntity mailbox, String to, String subject,
                          String htmlBody, Long ticketId) {
        sendReply(mailbox, to, subject, htmlBody, ticketId, List.of());
    }

    /** Same as the 5-arg overload, with file attachments — added for FEAT-05.4 scheduled report
     * delivery (CSV/PDF). Every existing caller is unaffected since it just delegates here with
     * an empty attachment list. */
    public void sendReply(EmailMailboxEntity mailbox, String to, String subject,
                          String htmlBody, Long ticketId, List<EmailAttachment> attachments) {
        if (mailbox == null || !Boolean.TRUE.equals(mailbox.getCanSend())) return;
        List<EmailAttachment> safeAttachments = attachments != null ? attachments : List.of();
        try {
            switch (mailbox.getProtocolType()) {
                case "IMAP_SMTP" -> sendSmtp(mailbox, to, subject, htmlBody, ticketId, safeAttachments);
                case "OAUTH2_GMAIL" -> sendGmail(mailbox, to, subject, htmlBody, ticketId, safeAttachments);
                case "OAUTH2_MICROSOFT" -> sendMicrosoft(mailbox, to, subject, htmlBody, ticketId, safeAttachments);
            }
        } catch (Exception e) {
            log.error("Failed to send email reply via mailbox {}: {}", mailbox.getId(), e.getMessage(), e);
        }
    }

    public Optional<EmailMailboxEntity> getDefaultSender() {
        return mailboxRepo.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsDefaultSender()) && Boolean.TRUE.equals(m.getIsActive()))
                .findFirst();
    }

    private void sendSmtp(EmailMailboxEntity mailbox, String to, String subject,
                          String htmlBody, Long ticketId, List<EmailAttachment> attachments) throws Exception {
        Properties props = new Properties();
        String protocol = Boolean.TRUE.equals(mailbox.getSmtpSsl()) ? "smtps" : "smtp";
        props.put("mail.transport.protocol", protocol);
        props.put("mail.smtp.host", mailbox.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(mailbox.getSmtpPort()));
        props.put("mail.smtp.auth", "true");
        if (Boolean.TRUE.equals(mailbox.getSmtpSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        String password = mailbox.getPasswordEncrypted() != null
                ? aesEncryptionUtils.decrypt(mailbox.getPasswordEncrypted()) : "";

        jakarta.mail.Session session = jakarta.mail.Session.getInstance(props,
                new jakarta.mail.Authenticator() {
                    @Override
                    protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                        return new jakarta.mail.PasswordAuthentication(mailbox.getUsername(), password);
                    }
                });

        jakarta.mail.Message message = new jakarta.mail.internet.MimeMessage(session);
        message.setFrom(new jakarta.mail.internet.InternetAddress(mailbox.getEmailAddress()));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO,
                jakarta.mail.internet.InternetAddress.parse(to));
        message.setSubject(subject);

        if (attachments.isEmpty()) {
            message.setContent(htmlBody, "text/html; charset=utf-8");
        } else {
            jakarta.mail.internet.MimeBodyPart bodyPart = new jakarta.mail.internet.MimeBodyPart();
            bodyPart.setContent(htmlBody, "text/html; charset=utf-8");

            jakarta.mail.internet.MimeMultipart multipart = new jakarta.mail.internet.MimeMultipart("mixed");
            multipart.addBodyPart(bodyPart);
            for (EmailAttachment att : attachments) {
                jakarta.mail.internet.MimeBodyPart attachPart = new jakarta.mail.internet.MimeBodyPart();
                attachPart.setDataHandler(new DataHandler(
                        new ByteArrayDataSource(att.data(), att.contentType())));
                attachPart.setFileName(att.filename());
                multipart.addBodyPart(attachPart);
            }
            message.setContent(multipart);
        }

        jakarta.mail.Transport.send(message);
        logOutbound(mailbox.getId(), ticketId, subject);
    }

    private void sendGmail(EmailMailboxEntity mailbox, String to, String subject,
                           String htmlBody, Long ticketId, List<EmailAttachment> attachments) throws Exception {
        String token = emailMailboxService.getValidGmailToken(mailbox);
        if (token == null) { log.warn("No valid Gmail token for mailbox {}", mailbox.getId()); return; }

        // Build RFC 2822 message
        String rawMessage = attachments.isEmpty()
                ? buildRawMessage(mailbox.getEmailAddress(), to, subject, htmlBody)
                : buildRawMessageWithAttachments(mailbox.getEmailAddress(), to, subject, htmlBody, attachments);
        String encodedMessage = Base64.getUrlEncoder().encodeToString(rawMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ObjectMapper mapper = new ObjectMapper();
        String payload = mapper.writeValueAsString(Map.of("raw", encodedMessage));

        HttpRequest req = HttpRequest.newBuilder(new URI("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();

        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            log.error("Gmail send failed {}: {}", res.statusCode(), res.body());
        } else {
            logOutbound(mailbox.getId(), ticketId, subject);
        }
    }

    private void sendMicrosoft(EmailMailboxEntity mailbox, String to, String subject,
                                String htmlBody, Long ticketId, List<EmailAttachment> attachments) throws Exception {
        String token = emailMailboxService.getValidMicrosoftToken(mailbox);
        if (token == null) { log.warn("No valid Microsoft token for mailbox {}", mailbox.getId()); return; }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> messageMap = new java.util.LinkedHashMap<>();
        messageMap.put("subject", subject);
        messageMap.put("body", Map.of("contentType", "HTML", "content", htmlBody));
        messageMap.put("toRecipients", List.of(Map.of("emailAddress", Map.of("address", to))));
        if (!attachments.isEmpty()) {
            List<Map<String, Object>> graphAttachments = new ArrayList<>();
            for (EmailAttachment att : attachments) {
                graphAttachments.add(Map.of(
                        "@odata.type", "#microsoft.graph.fileAttachment",
                        "name", att.filename(),
                        "contentType", att.contentType(),
                        "contentBytes", Base64.getEncoder().encodeToString(att.data())
                ));
            }
            messageMap.put("attachments", graphAttachments);
        }
        Map<String, Object> payload = Map.of("message", messageMap, "saveToSentItems", true);

        HttpRequest req = HttpRequest.newBuilder(new URI("https://graph.microsoft.com/v1.0/me/sendMail"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();

        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            log.error("Microsoft send failed {}: {}", res.statusCode(), res.body());
        } else {
            logOutbound(mailbox.getId(), ticketId, subject);
        }
    }

    private String buildRawMessage(String from, String to, String subject, String htmlBody) {
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "\r\n"
                + htmlBody;
    }

    /** Hand-built RFC 2822 multipart/mixed message — Gmail's send API only accepts a single raw
     * base64 message, so attachments have to be assembled as MIME parts ourselves rather than via
     * jakarta.mail (which the SMTP path uses instead). */
    private String buildRawMessageWithAttachments(String from, String to, String subject, String htmlBody,
                                                    List<EmailAttachment> attachments) {
        String boundary = "----turbotikects-" + UUID.randomUUID();
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append("\r\n");
        sb.append("To: ").append(to).append("\r\n");
        sb.append("Subject: ").append(subject).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");

        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: text/html; charset=utf-8\r\n\r\n");
        sb.append(htmlBody).append("\r\n\r\n");

        for (EmailAttachment att : attachments) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Type: ").append(att.contentType()).append("; name=\"").append(att.filename()).append("\"\r\n");
            sb.append("Content-Disposition: attachment; filename=\"").append(att.filename()).append("\"\r\n");
            sb.append("Content-Transfer-Encoding: base64\r\n\r\n");
            sb.append(Base64.getMimeEncoder(76, "\r\n".getBytes()).encodeToString(att.data()));
            sb.append("\r\n\r\n");
        }
        sb.append("--").append(boundary).append("--");
        return sb.toString();
    }

    private void logOutbound(Long mailboxId, Long ticketId, String subject) {
        EmailMessageLogEntity log = new EmailMessageLogEntity();
        log.setMailboxId(mailboxId);
        log.setMessageId("out-" + System.currentTimeMillis() + "-" + subject.hashCode());
        log.setTicketId(ticketId);
        log.setDirection("outbound");
        messageLogRepo.save(log);
    }
}
