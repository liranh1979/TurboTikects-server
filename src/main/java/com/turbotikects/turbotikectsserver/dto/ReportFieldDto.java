package com.turbotikects.turbotikectsserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry in the field catalog the AI query-builder agent (and the manual builder UI) sees. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportFieldDto {
    private String fieldKey;
    private String label;
    private String fieldType;
    private boolean isCustom;
}
