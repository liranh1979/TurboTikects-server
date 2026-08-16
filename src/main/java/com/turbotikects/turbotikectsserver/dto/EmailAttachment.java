package com.turbotikects.turbotikectsserver.dto;

/** A file to attach to an outbound email — added for FEAT-05.4 scheduled report delivery, but
 * generic enough for any future feature needing attachments via EmailSenderService. */
public record EmailAttachment(String filename, String contentType, byte[] data) {
}
