package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class SaveTicketLabelRequestDto {
    private String labelKey;
    private String color;
    private String name;
}
