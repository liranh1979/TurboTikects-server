package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class AlertTypeDto {
    private Long id;
    private String typeKey;
    private String color;
    private String icon;
    private Boolean isSystem;
    private Integer displayOrder;
}
