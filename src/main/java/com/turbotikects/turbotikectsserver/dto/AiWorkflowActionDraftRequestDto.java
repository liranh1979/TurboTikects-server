package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiWorkflowActionDraftRequestDto {
    private String documentation;
    private String intent;
    private List<String> ticketFieldKeys;
}
