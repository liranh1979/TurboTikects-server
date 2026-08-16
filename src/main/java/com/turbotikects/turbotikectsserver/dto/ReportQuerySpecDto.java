package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** The validated, whitelisted shape persisted to report_definitions.query_spec — never raw SQL. */
@Data
public class ReportQuerySpecDto {
    private List<String> selectedFields;
    private Map<String, Object> conditions;
}
