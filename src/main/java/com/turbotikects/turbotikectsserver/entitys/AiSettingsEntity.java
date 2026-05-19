package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_settings")
@lombok.Data
public class AiSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // This matches your image
    private Long id;

    @Column(name = "provider_name",  nullable = false)
    private String providerName;

    @Column(name = "api_key",  nullable = false)
    private String apiKey;

    @Column(name = "base_url",  nullable = false)
    private String baseUrl;

    @Column(name = "model_name",  nullable = false)
    private String modelName;

    @Column(name = "is_active",  nullable = false)
    private boolean isActive;

    @Column(name = "created_at",  nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at",  nullable = false)
    private LocalDateTime updatedAt;
}
