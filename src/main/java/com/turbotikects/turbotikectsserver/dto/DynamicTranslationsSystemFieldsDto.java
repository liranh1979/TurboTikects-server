package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DynamicTranslationsSystemFieldsDto {

    @JsonProperty("translation_key")
    String translationKey;

    @JsonProperty("translated_text")
    String translatedText;

}
