package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KbArticleDetailDto {
    private Long id;
    private String title;
    private String body;
    private Long categoryId;
    private String categoryName;
    private List<TicketLabelDto> labels;
    private String visibility;
    private Integer viewCount;
    private Integer helpfulCount;
    private Integer notHelpfulCount;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
