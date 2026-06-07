package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkUpdateStatusDto {
    private List<Long> ids;
    private String status;
}
