package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class LdapTestResultDto {

    private boolean success;
    private String message;

    public static LdapTestResultDto ok() {
        LdapTestResultDto r = new LdapTestResultDto();
        r.success = true;
        r.message = "Connection successful";
        return r;
    }

    public static LdapTestResultDto fail(String message) {
        LdapTestResultDto r = new LdapTestResultDto();
        r.success = false;
        r.message = message;
        return r;
    }
}
