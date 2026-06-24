package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
public class SystemSettingsEntity {

    @Id
    private Integer id = 1;

    @Column(name = "default_language_code", nullable = false, length = 5)
    private String defaultLanguageCode = "en";

    @Column(name = "default_timezone", nullable = false, length = 64)
    private String defaultTimezone = "UTC";

    @Column(name = "default_time_format", nullable = false, length = 3)
    private String defaultTimeFormat = "24h";

    @Column(name = "logo_path", length = 255)
    private String logoPath;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() { updatedAt = LocalDateTime.now(); }
}
