package com.turbotikects.turbotikectsserver.services;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AttachmentTokenStore {

    private record TokenEntry(long attachmentId, Instant expiresAt) {}

    private final ConcurrentHashMap<String, TokenEntry> store = new ConcurrentHashMap<>();

    public String issue(long attachmentId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new TokenEntry(attachmentId, Instant.now().plusSeconds(3600)));
        return token;
    }

    public Optional<Long> validate(String token) {
        TokenEntry e = store.get(token);
        if (e == null) return Optional.empty();
        if (e.expiresAt().isBefore(Instant.now())) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(e.attachmentId());
    }
}
