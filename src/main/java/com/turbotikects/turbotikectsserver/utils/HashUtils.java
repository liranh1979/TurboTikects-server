package com.turbotikects.turbotikectsserver.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Formatter;

public class HashUtils {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    public static String bcrypt(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    public static boolean matchesBcrypt(String rawPassword, String hash) {
        return BCRYPT.matches(rawPassword, hash);
    }

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
