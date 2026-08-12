package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class KbCategoryDto {
    private Long id;
    private String name;
    private String icon;
    private Integer displayOrder;
}
