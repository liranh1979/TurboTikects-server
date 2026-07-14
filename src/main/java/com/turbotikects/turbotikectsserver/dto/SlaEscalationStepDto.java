package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SlaEscalationStepDto {
    private Long id;
    private Integer stepOrder;
    private String triggerType;
    private BigDecimal triggerValue;
    private String actionType;
    private Integer targetUserId;
    private Integer targetGroupId;
    private String webhookUrl;
}
