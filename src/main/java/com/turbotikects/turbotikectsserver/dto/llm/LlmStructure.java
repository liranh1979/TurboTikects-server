package com.turbotikects.turbotikectsserver.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmStructure {
    String role;
    String content;
}
