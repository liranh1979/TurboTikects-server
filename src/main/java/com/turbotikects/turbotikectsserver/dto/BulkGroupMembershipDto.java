package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class BulkGroupMembershipDto {
    private List<Long> userIds;
    private List<Long> groupIds;
}
