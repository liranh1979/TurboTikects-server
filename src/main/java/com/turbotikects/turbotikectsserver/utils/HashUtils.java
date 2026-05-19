package com.turbotikects.turbotikectsserver.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Formatter;

public class HashUtils {

    public static String sha1(String input) {
        try {
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(input.getBytes(StandardCharsets.UTF_8));
            return byteToHex(crypt.digest());
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 hashing failed", e);
        }
    }

    private static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
}
