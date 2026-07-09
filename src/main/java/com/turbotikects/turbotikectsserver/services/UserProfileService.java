package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.ChangePasswordDto;
import com.turbotikects.turbotikectsserver.dto.SelfProfileDto;
import com.turbotikects.turbotikectsserver.dto.SelfProfileFieldDto;
import com.turbotikects.turbotikectsserver.dto.UpdateOwnProfileDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;

    public UserProfileService(UserRepository userRepository, FieldDefinitionsRepository fieldDefinitionsRepository) {
        this.userRepository = userRepository;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
    }

    private UserEntity getOwnEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public SelfProfileDto getOwnProfile(Long userId) {
        UserEntity user = getOwnEntity(userId);

        SelfProfileDto dto = new SelfProfileDto();
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setEmail(user.getEmail());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setCanChangePassword(user.getSourceType() == 0);

        List<SelfProfileFieldDto> fields = new ArrayList<>();
        for (FieldDefinitionsEntity f : fieldDefinitionsRepository.findByEntityTypeOrderByDisplayOrder("user")) {
            if ("admin_only".equals(f.getFieldVisibility())) continue; // hidden entirely from self-view

            SelfProfileFieldDto fd = new SelfProfileFieldDto();
            fd.setFieldKey(f.getFieldKey());
            fd.setFieldType(f.getFieldType());
            fd.setFieldOptions(f.getFieldOptions());
            fd.setEditable("all".equals(f.getFieldVisibility()));
            fd.setValue(extractValue(user.getMetadata() != null ? user.getMetadata().get(f.getFieldKey()) : null));
            fields.add(fd);
        }
        dto.setFields(fields);
        return dto;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public void updateOwnProfile(Long userId, UpdateOwnProfileDto dto) {
        UserEntity user = getOwnEntity(userId);

        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) {
            user.setDisplayName(dto.getDisplayName().trim());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail().isBlank() ? null : dto.getEmail().trim().toLowerCase());
        }
        if (dto.getPreferredLanguage() != null) {
            user.setPreferredLanguage(dto.getPreferredLanguage().isBlank() ? null : dto.getPreferredLanguage());
        }

        if (dto.getMetadata() != null && !dto.getMetadata().isEmpty()) {
            // Defense in depth: only accept keys whose field_visibility is exactly "all" —
            // the frontend won't send anything else, but never trust the client for this.
            List<FieldDefinitionsEntity> userFields =
                    fieldDefinitionsRepository.findByEntityTypeOrderByDisplayOrder("user");
            Map<String, FieldDefinitionsEntity> editableByKey = new HashMap<>();
            for (FieldDefinitionsEntity f : userFields) {
                if ("all".equals(f.getFieldVisibility())) editableByKey.put(f.getFieldKey(), f);
            }

            Map<String, Object> merged = user.getMetadata() != null
                    ? new HashMap<>(user.getMetadata()) : new HashMap<>();
            for (Map.Entry<String, String> entry : dto.getMetadata().entrySet()) {
                if (!editableByKey.containsKey(entry.getKey())) continue;
                Object existing = merged.get(entry.getKey());
                Map<String, Object> wrapped = (existing instanceof Map)
                        ? new HashMap<>((Map<String, Object>) existing) : new HashMap<>();
                wrapped.put("translation_key", entry.getKey());
                wrapped.put("value", entry.getValue());
                merged.put(entry.getKey(), wrapped);
            }
            user.setMetadata(merged);
        }

        userRepository.save(user);
    }

    @Transactional
    public void changeOwnPassword(Long userId, ChangePasswordDto dto) {
        UserEntity user = getOwnEntity(userId);

        if (user.getSourceType() != 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your password is managed by your organization and cannot be changed here.");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        if (!HashUtils.matchesBcrypt(dto.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        user.setPassword(HashUtils.bcrypt(dto.getNewPassword()));
        userRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private String extractValue(Object raw) {
        if (raw == null) return "";
        if (raw instanceof Map) {
            Object v = ((Map<String, Object>) raw).get("value");
            return v != null ? String.valueOf(v) : "";
        }
        return String.valueOf(raw);
    }
}
