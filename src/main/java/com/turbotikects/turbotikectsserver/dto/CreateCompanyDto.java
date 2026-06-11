package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class CreateCompanyDto {
    private String name;
    private String description;
    private String timezone;
}
