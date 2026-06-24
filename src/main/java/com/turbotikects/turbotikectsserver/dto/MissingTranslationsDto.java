package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MissingTranslationsDto {
    private int count;
    private List<String> sample;
    // Full list (not capped) so the client can drive its own batched-translate + progress
    // bar flow, the same way the System Fields / custom-field grids already do.
    private List<MissingTranslationEntryDto> entries;
}
