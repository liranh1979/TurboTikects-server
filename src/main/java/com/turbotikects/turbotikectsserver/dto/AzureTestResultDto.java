package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class AzureTestResultDto {

    private boolean success;
    private String message;

    public static AzureTestResultDto ok() {
        AzureTestResultDto r = new AzureTestResultDto();
        r.success = true;
        r.message = "Connection successful";
        return r;
    }

    public static AzureTestResultDto fail(String message) {
        AzureTestResultDto r = new AzureTestResultDto();
        r.success = false;
        r.message = message;
        return r;
    }
}
