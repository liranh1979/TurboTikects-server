package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** FEAT-06 Phase 7 — "test this call now" result shape, shared by both ExternalApiActionExecutor.testRun and McpActionExecutor.testRun so the Designer's result panel is one component for both action types. */
@Data
public class WorkflowActionTestResult {
    private boolean success;
    private String error;
    private Map<String, Object> capturedValues;
    private List<Map<String, Object>> callTrace;

    public static WorkflowActionTestResult success(Map<String, Object> capturedValues, List<Map<String, Object>> callTrace) {
        WorkflowActionTestResult r = new WorkflowActionTestResult();
        r.success = true;
        r.capturedValues = capturedValues;
        r.callTrace = callTrace;
        return r;
    }

    public static WorkflowActionTestResult failure(String error, List<Map<String, Object>> callTrace) {
        WorkflowActionTestResult r = new WorkflowActionTestResult();
        r.success = false;
        r.error = error;
        r.callTrace = callTrace;
        return r;
    }
}
