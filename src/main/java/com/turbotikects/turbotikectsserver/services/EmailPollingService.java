package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import com.turbotikects.turbotikectsserver.repositorys.EmailMailboxRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class EmailPollingService {

    @Autowired private EmailMailboxRepository mailboxRepo;
    @Autowired private EmailProcessorService processorService;
    @Autowired private EmailMailboxService mailboxService;
    @Autowired private com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils aesEncryptionUtils;

    private final ObjectMapper mapper = new ObjectMapper();

    @Scheduled(fixedDelay = 60_000)
    public void pollAllMailboxes() {
        List<EmailMailboxEntity> mailboxes = mailboxRepo.findByIsActiveTrueAndCanReceiveTrue();
        for (EmailMailboxEntity mailbox : mailboxes) {
            try {
                if (!isDue(mailbox)) continue;
                poll(mailbox);
                mailbox.setLastCheckedAt(LocalDateTime.now());
                mailboxRepo.save(mailbox);
            } catch (Exception e) {
                log.error("Error polling mailbox {}: {}", mailbox.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean isDue(EmailMailboxEntity mailbox) {
        if (mailbox.getLastCheckedAt() == null) return true;
        int interval = mailbox.getPollIntervalSeconds() != null ? mailbox.getPollIntervalSeconds() : 300;
        return mailbox.getLastCheckedAt().plusSeconds(interval).isBefore(LocalDateTime.now());
    }

    private void poll(EmailMailboxEntity mailbox) throws Exception {
        log.info("Polling mailbox {} ({})", mailbox.getId(), mailbox.getEmailAddress());
        switch (mailbox.getProtocolType()) {
            case "IMAP_SMTP" -> pollImap(mailbox);
            case "OAUTH2_GMAIL" -> pollGmail(mailbox);
            case "OAUTH2_MICROSOFT" -> pollMicrosoft(mailbox);
        }
    }

    // ── IMAP ─────────────────────────────────────────────────────────────────────

    private void pollImap(EmailMailboxEntity mailbox) throws Exception {
        String protocol = Boolean.TRUE.equals(mailbox.getImapSsl()) ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", mailbox.getImapHost());
        props.put("mail." + protocol + ".port", String.valueOf(mailbox.getImapPort()));
        props.put("mail." + protocol + ".ssl.enable", Boolean.TRUE.equals(mailbox.getImapSsl()) ? "true" : "false");
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "10000");

        String password = mailbox.getPasswordEncrypted() != null
                ? aesEncryptionUtils.decrypt(mailbox.getPasswordEncrypted())
                : "";

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(mailbox.getImapHost(), mailbox.getUsername(), password);
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        log.info("Found {} unread messages in mailbox {}", messages.length, mailbox.getId());

        for (Message msg : messages) {
            try {
                EmailProcessorService.InboundEmail email = extractImapMessage(msg);
                processorService.process(mailbox, email);
                msg.setFlag(Flags.Flag.SEEN, true);
            } catch (Exception e) {
                log.error("Error processing IMAP message: {}", e.getMessage(), e);
            }
        }

        inbox.close(false);
        store.close();
    }

    private EmailProcessorService.InboundEmail extractImapMessage(Message msg) throws Exception {
        EmailProcessorService.InboundEmail email = new EmailProcessorService.InboundEmail();

        // Message-ID header
        String[] msgIds = msg.getHeader("Message-ID");
        email.setMessageId(msgIds != null && msgIds.length > 0 ? msgIds[0] : null);

        // From
        Address[] from = msg.getFrom();
        if (from != null && from.length > 0) {
            InternetAddress ia = (InternetAddress) from[0];
            email.setFromAddress(ia.getAddress());
            email.setFromName(ia.getPersonal());
        }

        email.setSubject(msg.getSubject());

        // Body extraction
        extractBody(msg.getContent(), msg.getContentType(), email);
        return email;
    }

    private void extractBody(Object content, String contentType, EmailProcessorService.InboundEmail email) throws Exception {
        if (content instanceof String text) {
            if (contentType != null && contentType.toLowerCase().contains("html")) {
                email.setHtmlBody(text);
            } else {
                email.setTextBody(text);
            }
        } else if (content instanceof MimeMultipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                String ct = part.getContentType().toLowerCase();
                if (ct.contains("text/html")) {
                    email.setHtmlBody((String) part.getContent());
                } else if (ct.contains("text/plain") && email.getTextBody() == null) {
                    email.setTextBody((String) part.getContent());
                } else if (ct.contains("multipart/")) {
                    extractBody(part.getContent(), ct, email);
                } else {
                    // Collect inline images (Content-ID present)
                    String[] cids = part.getHeader("Content-ID");
                    if (cids != null && cids.length > 0) {
                        String cid = cids[0].replaceAll("[<>\\s]", "");
                        try {
                            byte[] bytes = part.getInputStream().readAllBytes();
                            String mimeClean = ct.split(";")[0].trim();
                            String filename = part.getFileName() != null ? part.getFileName() : cid;
                            email.addInlineAttachment(cid,
                                    new EmailProcessorService.InboundEmail.InlineAttachment(bytes, mimeClean, filename));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    // ── Gmail ─────────────────────────────────────────────────────────────────────

    private void pollGmail(EmailMailboxEntity mailbox) throws Exception {
        String token = mailboxService.getValidGmailToken(mailbox);
        if (token == null) { log.warn("No Gmail token for mailbox {}", mailbox.getId()); return; }

        // List unread messages
        HttpRequest listReq = HttpRequest.newBuilder(
                new URI("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=is:unread+in:inbox&maxResults=50"))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> listRes = HttpClient.newHttpClient().send(listReq, HttpResponse.BodyHandlers.ofString());
        if (listRes.statusCode() != 200) return;

        JsonNode root = mapper.readTree(listRes.body());
        JsonNode msgs = root.get("messages");
        if (msgs == null || msgs.isEmpty()) return;

        for (JsonNode msgNode : msgs) {
            String msgId = msgNode.get("id").asText();
            try {
                processGmailMessage(mailbox, token, msgId);
            } catch (Exception e) {
                log.error("Error processing Gmail message {}: {}", msgId, e.getMessage());
            }
        }
    }

    private void processGmailMessage(EmailMailboxEntity mailbox, String token, String gmailId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                new URI("https://gmail.googleapis.com/gmail/v1/users/me/messages/" + gmailId + "?format=full"))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return;

        JsonNode msg = mapper.readTree(res.body());
        EmailProcessorService.InboundEmail email = parseGmailMessage(msg, token, gmailId);
        processorService.process(mailbox, email);

        // Mark as read
        String markBody = "{\"removeLabelIds\":[\"UNREAD\"]}";
        HttpRequest markReq = HttpRequest.newBuilder(
                new URI("https://gmail.googleapis.com/gmail/v1/users/me/messages/" + gmailId + "/modify"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(markBody)).build();
        HttpClient.newHttpClient().send(markReq, HttpResponse.BodyHandlers.ofString());
    }

    private EmailProcessorService.InboundEmail parseGmailMessage(JsonNode msg, String token, String gmailId) {
        EmailProcessorService.InboundEmail email = new EmailProcessorService.InboundEmail();
        email.setMessageId(getHeader(msg, "Message-ID"));
        email.setSubject(getHeader(msg, "Subject"));
        String from = getHeader(msg, "From");
        if (from != null) {
            int lt = from.indexOf('<');
            if (lt >= 0) {
                email.setFromName(from.substring(0, lt).trim());
                email.setFromAddress(from.substring(lt + 1).replace(">", "").trim());
            } else {
                email.setFromAddress(from.trim());
            }
        }
        JsonNode payload = msg.get("payload");
        if (payload != null) {
            extractGmailBody(payload, email, token, gmailId);
        }
        return email;
    }

    private void extractGmailBody(JsonNode payload, EmailProcessorService.InboundEmail email,
                                   String token, String gmailId) {
        String mimeType = payload.has("mimeType") ? payload.get("mimeType").asText() : "";
        if (mimeType.equals("text/html")) {
            email.setHtmlBody(decodeBase64(payload.path("body").path("data").asText("")));
        } else if (mimeType.equals("text/plain") && email.getTextBody() == null) {
            email.setTextBody(decodeBase64(payload.path("body").path("data").asText("")));
        } else if (mimeType.startsWith("image/")) {
            String cid = getPartHeader(payload, "Content-ID");
            if (cid != null) {
                cid = cid.replaceAll("[<>\\s]", "");
                String bodyData = payload.path("body").path("data").asText("");
                byte[] bytes = null;
                if (!bodyData.isEmpty()) {
                    try { bytes = java.util.Base64.getUrlDecoder().decode(bodyData); } catch (Exception ignored) {}
                } else {
                    String attachmentId = payload.path("body").path("attachmentId").asText("");
                    if (!attachmentId.isEmpty()) bytes = fetchGmailAttachmentBytes(token, gmailId, attachmentId);
                }
                if (bytes != null) {
                    String filename = getPartHeader(payload, "Content-Disposition");
                    if (filename == null || filename.isBlank()) filename = cid;
                    email.addInlineAttachment(cid,
                            new EmailProcessorService.InboundEmail.InlineAttachment(bytes, mimeType, filename));
                }
            }
        } else if (mimeType.startsWith("multipart/")) {
            JsonNode parts = payload.get("parts");
            if (parts != null) {
                for (JsonNode part : parts) {
                    extractGmailBody(part, email, token, gmailId);
                }
            }
        }
    }

    private byte[] fetchGmailAttachmentBytes(String token, String messageId, String attachmentId) {
        try {
            HttpRequest req = HttpRequest.newBuilder(new URI(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + messageId + "/attachments/" + attachmentId))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                String data = mapper.readTree(res.body()).path("data").asText("");
                if (!data.isEmpty()) return java.util.Base64.getUrlDecoder().decode(data);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Gmail attachment: {}", e.getMessage());
        }
        return null;
    }

    private String getPartHeader(JsonNode part, String name) {
        JsonNode headers = part.path("headers");
        for (JsonNode h : headers) {
            if (name.equalsIgnoreCase(h.path("name").asText())) return h.path("value").asText();
        }
        return null;
    }

    private String getHeader(JsonNode msg, String name) {
        JsonNode headers = msg.path("payload").path("headers");
        if (headers.isMissingNode()) headers = msg.path("headers");
        for (JsonNode h : headers) {
            if (name.equalsIgnoreCase(h.path("name").asText())) return h.path("value").asText();
        }
        return null;
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        try {
            return new String(java.util.Base64.getUrlDecoder().decode(encoded));
        } catch (Exception e) {
            return encoded;
        }
    }

    // ── Microsoft ──────────────────────────────────────────────────────────────────

    private void pollMicrosoft(EmailMailboxEntity mailbox) throws Exception {
        String token = mailboxService.getValidMicrosoftToken(mailbox);
        if (token == null) { log.warn("No Microsoft token for mailbox {}", mailbox.getId()); return; }

        String url = "https://graph.microsoft.com/v1.0/me/messages"
                + "?$filter=isRead eq false"
                + "&$select=id,subject,from,body,internetMessageId,hasAttachments"
                + "&$top=50";

        HttpRequest req = HttpRequest.newBuilder(new URI(url))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return;

        JsonNode root = mapper.readTree(res.body());
        JsonNode values = root.get("value");
        if (values == null || values.isEmpty()) return;

        for (JsonNode msgNode : values) {
            try {
                String graphMsgId = msgNode.path("id").asText();
                EmailProcessorService.InboundEmail email = parseMicrosoftMessage(msgNode);
                if (msgNode.path("hasAttachments").asBoolean(false)) {
                    fetchMicrosoftInlineAttachments(token, graphMsgId, email);
                }
                processorService.process(mailbox, email);

                // Mark as read
                String msgId = msgNode.get("id").asText();
                HttpRequest markReq = HttpRequest.newBuilder(
                        new URI("https://graph.microsoft.com/v1.0/me/messages/" + msgId))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"isRead\":true}")).build();
                HttpClient.newHttpClient().send(markReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                log.error("Error processing Microsoft message: {}", e.getMessage());
            }
        }
    }

    private void fetchMicrosoftInlineAttachments(String token, String graphMsgId,
                                                   EmailProcessorService.InboundEmail email) {
        try {
            HttpRequest req = HttpRequest.newBuilder(new URI(
                    "https://graph.microsoft.com/v1.0/me/messages/" + graphMsgId + "/attachments"))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonNode values = mapper.readTree(res.body()).path("value");
            for (JsonNode att : values) {
                if (!att.path("isInline").asBoolean(false)) continue;
                String cid = att.path("contentId").asText(null);
                if (cid == null) continue;
                cid = cid.replaceAll("[<>\\s]", "");
                String bytes64 = att.path("contentBytes").asText(null);
                if (bytes64 == null || bytes64.isEmpty()) continue;
                byte[] bytes = java.util.Base64.getDecoder().decode(bytes64);
                String mimeType = att.path("contentType").asText("application/octet-stream");
                String filename = att.path("name").asText(cid);
                email.addInlineAttachment(cid,
                        new EmailProcessorService.InboundEmail.InlineAttachment(bytes, mimeType, filename));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Microsoft inline attachments for {}: {}", graphMsgId, e.getMessage());
        }
    }

    private EmailProcessorService.InboundEmail parseMicrosoftMessage(JsonNode msg) {
        EmailProcessorService.InboundEmail email = new EmailProcessorService.InboundEmail();
        email.setMessageId(msg.path("internetMessageId").asText(null));
        email.setSubject(msg.path("subject").asText(null));

        JsonNode fromNode = msg.path("from").path("emailAddress");
        email.setFromAddress(fromNode.path("address").asText(null));
        email.setFromName(fromNode.path("name").asText(null));

        JsonNode body = msg.path("body");
        String contentType = body.path("contentType").asText("Text");
        String content = body.path("content").asText("");
        if ("HTML".equalsIgnoreCase(contentType)) {
            email.setHtmlBody(content);
        } else {
            email.setTextBody(content);
        }
        return email;
    }
}
