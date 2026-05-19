package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dynamic_translations")
@lombok.Data
public class DynamicTranslationsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // This matches your image
    private Long id;

    @Column(name = "lang_code",  nullable = false)
    private String langCode;

    @Column(name = "translation_key",  nullable = false)
    private String translationKey;

    @Column(name = "translated_text",  nullable = false)
    private String translatedText;

    @Column(name = "updated_at",  nullable = true)
    private LocalDateTime updateData;

    @Column(name = "type", nullable = false)
    private String type;

}
