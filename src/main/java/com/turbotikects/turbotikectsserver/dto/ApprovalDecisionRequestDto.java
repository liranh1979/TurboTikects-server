package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

@Data
public class ApprovalDecisionRequestDto {
    private String decision; // 'approved' | 'rejected'
    private String reason;   // required in practice for 'rejected', optional otherwise
}
