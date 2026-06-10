package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class EmailConnectionTestResultDto {
    private boolean success;
    private String message;

    public EmailConnectionTestResultDto() {}

    public EmailConnectionTestResultDto(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
