package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class TicketLabelDto {
    private Long id;
    private String labelKey;
    private String color;
    private String name;
}
