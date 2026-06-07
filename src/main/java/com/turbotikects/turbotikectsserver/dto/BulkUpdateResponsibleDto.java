package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkUpdateResponsibleDto {
    private List<Long> ids;
    private Integer responsibleUserId;
    private Integer responsibleGroupId;
}
