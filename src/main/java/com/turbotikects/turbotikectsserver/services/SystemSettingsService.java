package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.MissingTranslationEntryDto;
import com.turbotikects.turbotikectsserver.dto.MissingTranslationsDto;
import com.turbotikects.turbotikectsserver.dto.SystemSettingsDto;
import com.turbotikects.turbotikectsserver.dto.UpdateSystemSettingsDto;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.entitys.SystemSettingsEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.SystemSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemSettingsService {

    private final SystemSettingsRepository settingsRepo;
    private final DynamicTranslationsRepository translationsRepo;
    private final FileStorageService fileStorage;

    public SystemSettingsService(SystemSettingsRepository settingsRepo,
                                  DynamicTranslationsRepository translationsRepo,
                                  FileStorageService fileStorage) {
        this.settingsRepo = settingsRepo;
        this.translationsRepo = translationsRepo;
        this.fileStorage = fileStorage;
    }

    private SystemSettingsEntity getEntity() {
        return settingsRepo.findById(1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "system_settings row is missing — Flyway migration V63 did not run"));
    }

    public SystemSettingsDto getPublicSettings() {
        return toDto(getEntity());
    }

    public String getDefaultTimezone() {
        return getEntity().getDefaultTimezone();
    }

    @Transactional
    public SystemSettingsDto updateSettings(UpdateSystemSettingsDto dto) {
        SystemSettingsEntity e = getEntity();
        if (dto.getDefaultLanguageCode() != null && !dto.getDefaultLanguageCode().isBlank()) {
            e.setDefaultLanguageCode(dto.getDefaultLanguageCode());
        }
        if (dto.getDefaultTimezone() != null && !dto.getDefaultTimezone().isBlank()) {
            e.setDefaultTimezone(dto.getDefaultTimezone());
        }
        if (dto.getDefaultTimeFormat() != null && !dto.getDefaultTimeFormat().isBlank()) {
            if (!"12h".equals(dto.getDefaultTimeFormat()) && !"24h".equals(dto.getDefaultTimeFormat())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "defaultTimeFormat must be '12h' or '24h'");
            }
            e.setDefaultTimeFormat(dto.getDefaultTimeFormat());
        }
        return toDto(settingsRepo.save(e));
    }

    @Transactional
    public SystemSettingsDto uploadLogo(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
        }
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase()
                : "";
        String relativePath = "branding/logo" + ext;

        SystemSettingsEntity e = getEntity();
        if (e.getLogoPath() != null && !e.getLogoPath().equals(relativePath) && fileStorage.exists(e.getLogoPath())) {
            fileStorage.delete(e.getLogoPath());
        }
        fileStorage.store(file.getInputStream(), relativePath, file.getSize());
        e.setLogoPath(relativePath);
        return toDto(settingsRepo.save(e));
    }

    public byte[] getLogoBytes() throws IOException {
        SystemSettingsEntity e = getEntity();
        if (e.getLogoPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No logo uploaded");
        }
        return fileStorage.retrieve(e.getLogoPath());
    }

    public String getLogoContentType() {
        String path = getEntity().getLogoPath();
        if (path == null) return "application/octet-stream";
        String guessed = URLConnection.guessContentTypeFromName(path);
        return guessed != null ? guessed : "image/png";
    }

    public MissingTranslationsDto getMissingTranslations(String langCode) {
        MissingTranslationsDto dto = new MissingTranslationsDto();
        if ("en".equals(langCode)) {
            dto.setCount(0);
            dto.setSample(List.of());
            dto.setEntries(List.of());
            return dto;
        }
        List<MissingEntry> missing = computeMissing(langCode);
        dto.setCount(missing.size());
        dto.setSample(missing.stream().limit(10).map(MissingEntry::key).collect(Collectors.toList()));
        // Full list (uncapped) — the client batches these itself via /ai/translate-bulk and
        // shows its own progress bar, the same way System Fields / custom-field grids do,
        // instead of this backend doing one big synchronous call per type with no visibility.
        dto.setEntries(missing.stream()
                .map(m -> new MissingTranslationEntryDto(m.type(), m.key(), m.englishText()))
                .collect(Collectors.toList()));
        return dto;
    }

    // type+key uniquely identifies a translation row; translationKey alone can collide across types
    // (e.g. the same field key reused for both ticket_fields and workflow_fields).
    private record MissingEntry(String type, String key, String englishText) {}

    private List<MissingEntry> computeMissing(String langCode) {
        List<DynamicTranslationsEntity> english = translationsRepo.findByLangCode("en");
        List<DynamicTranslationsEntity> target = translationsRepo.findByLangCode(langCode);

        Map<String, String> targetByComposite = new HashMap<>();
        for (DynamicTranslationsEntity e : target) {
            targetByComposite.put(e.getType() + "::" + e.getTranslationKey(), e.getTranslatedText());
        }

        List<MissingEntry> missing = new ArrayList<>();
        for (DynamicTranslationsEntity e : english) {
            String existing = targetByComposite.get(e.getType() + "::" + e.getTranslationKey());
            if (existing == null || existing.isBlank()) {
                missing.add(new MissingEntry(e.getType(), e.getTranslationKey(), e.getTranslatedText()));
            }
        }
        return missing;
    }

    private SystemSettingsDto toDto(SystemSettingsEntity e) {
        SystemSettingsDto dto = new SystemSettingsDto();
        dto.setDefaultLanguageCode(e.getDefaultLanguageCode());
        dto.setDefaultTimezone(e.getDefaultTimezone());
        dto.setDefaultTimeFormat(e.getDefaultTimeFormat());
        if (e.getLogoPath() != null) {
            int cacheBuster = e.getUpdatedAt() != null ? e.getUpdatedAt().hashCode() : 0;
            dto.setLogoUrl("/api/v1/system-settings/logo?v=" + cacheBuster);
        }
        return dto;
    }
}
