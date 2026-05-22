package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public interface LlmProvider {
    String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException;
    boolean supports(String providerName);
}