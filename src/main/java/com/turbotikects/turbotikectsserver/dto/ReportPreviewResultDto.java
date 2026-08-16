package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Response for both the AI-build-query endpoint and the manual preview endpoint — same shape,
 * so the frontend's "preview" panel doesn't need to branch on how the query was arrived at. */
@Data
public class ReportPreviewResultDto {
    private List<String> selectedFields;
    private Map<String, Object> conditions;
    private int matchCount;
    /** Capped (e.g. 50) — never the full result set, even if matchCount is larger. */
    private List<Map<String, Object>> previewRows;
}
