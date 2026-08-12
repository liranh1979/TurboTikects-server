package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class GenerateDraftResultDto {
    private String title;
    private String body;
    private List<Long> labelIds;
}
