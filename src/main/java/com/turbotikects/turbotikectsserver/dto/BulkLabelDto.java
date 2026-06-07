package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkLabelDto {
    private List<Long> ids;
    private Long labelId;
}
