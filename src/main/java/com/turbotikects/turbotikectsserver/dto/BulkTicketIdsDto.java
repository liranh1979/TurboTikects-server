package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkTicketIdsDto {
    private List<Long> ids;
}
