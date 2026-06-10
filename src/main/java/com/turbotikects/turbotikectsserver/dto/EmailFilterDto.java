package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class EmailFilterDto {
    private Long id;
    private Long mailboxId;
    private String listType;
    private String emailPattern;
}
