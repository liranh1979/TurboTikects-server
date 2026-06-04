package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentDto {
    private Long id;
    private String entityType;
    private Long entityId;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private Integer uploadedBy;
    private LocalDateTime createdAt;
}
