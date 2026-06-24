package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public interface LlmProvider {
    String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException;
    boolean supports(String providerName);
    AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception;

    // Default no-op so providers that don't (yet) support listing available models — i.e.
    // everything except Gemini today — don't need any change. Implementors return the raw
    // model ids that support text generation, e.g. "gemini-2.0-flash".
    default List<String> listModels(String apiKey) throws IOException, URISyntaxException, InterruptedException {
        return List.of();
    }
}