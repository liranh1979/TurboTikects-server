package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class AttachmentTokenResponseDto {
    private String token;
    private Instant expiresAt;
}
