package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class SaveKbArticleRequestDto {
    private String title;
    private String body;
    private Long categoryId;
    private List<Long> labelIds;
    private String visibility;
}
