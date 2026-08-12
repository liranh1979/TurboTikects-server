package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiReviewResultDto {
    private String proposedBody;
    private List<AiReviewChangeDto> changes;

    @Data
    public static class AiReviewChangeDto {
        private String type;
        private String description;
    }
}
