package com.turbotikects.turbotikectsserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class AiTranslatioDto {

    record Pair<K, V>(K key, V value) {}

    private String filedType;
    private String type;
    private String targetLanguage;
    private List<Pair<String, String>> pairList = new ArrayList<>();
}
