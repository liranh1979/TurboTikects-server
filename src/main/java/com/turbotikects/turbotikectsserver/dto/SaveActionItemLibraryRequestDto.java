package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SaveActionItemLibraryRequestDto {
    private String name;
    private String type;
    private Map<String, Object> typeConfig;
    private String source;
    // "draft" | "complete" — optional. On create, omitted means "complete" (back-compat for
    // manual saves that never send it). On update, omitted means "leave the existing status
    // alone" — a plain field-tweak PUT must never silently complete a draft.
    private String status;
}
