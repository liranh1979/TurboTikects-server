package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "dashboard_report_cache")
@Data
public class DashboardReportCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "dashboard_id", nullable = false, length = 64)
    private String dashboardId;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", columnDefinition = "json", nullable = false)
    private Map<String, Object> reportJson;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
