package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

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

    @Column(name = "acceleration_cron_interval", nullable = false)
    private Integer accelerationCronInterval = 5;

    // Order of the Tickets Dashboard's sections (e.g. ["ai_report","charts","csat","sla"]). Null/empty
    // means "use the built-in default" — resolved in SystemSettingsService, not seeded here.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dashboard_section_order", columnDefinition = "json")
    private List<String> dashboardSectionOrder;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() { updatedAt = LocalDateTime.now(); }
}
