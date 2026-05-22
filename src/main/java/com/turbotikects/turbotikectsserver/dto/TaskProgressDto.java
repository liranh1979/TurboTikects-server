package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class TaskProgressDto {
    private String id;
    private String name;
    private int total;
    private int current;
    private int percentage;
    private String message;
    private String status; // "running" | "completed" | "failed"
}