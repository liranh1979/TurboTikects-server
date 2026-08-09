package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class SaveAlertTypeRequestDto {
    private String name;
    private String typeKey;
    private String color;
    private String icon;
}
