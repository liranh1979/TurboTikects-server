package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_languages")
@lombok.Data
public class SystemLanguagesEntity {
    @Id
    @Column(name = "code")
    String code;
    @Column(name = "name",  nullable = false)
    String name;

    @Column(name = "created_at",  nullable = true)
    private LocalDateTime createdAt ;
}