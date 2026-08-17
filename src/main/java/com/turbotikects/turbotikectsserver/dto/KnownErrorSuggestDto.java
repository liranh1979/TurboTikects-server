package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class KnownErrorSuggestDto {
    private Long problemId;
    private String title;
    private String workaroundPlainText;
}
