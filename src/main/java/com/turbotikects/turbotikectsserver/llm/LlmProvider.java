package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public interface LlmProvider {
    String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException;

    // expectJson=false lets a free-form conversational call (e.g. the "Consult AI" ticket chat)
    // opt out of JSON-mode constrained decoding — several providers force it unconditionally on
    // the 2-arg send() above for every structured-extraction AI feature, which was found live to
    // also force a plain chat reply into syntactically-valid-but-irrelevant JSON. Default
    // delegates to the 2-arg method (expectJson=true behavior) so every existing provider that
    // doesn't override this (Anthropic, GeminiStable — neither forces JSON today anyway) and every
    // existing call site (every JSON-expecting AI feature already in this codebase) is unaffected.
    default String send(AiSettingsEntity settings, List<LlmStructure> messages, boolean expectJson)
            throws IOException, URISyntaxException, InterruptedException {
        return send(settings, messages);
    }

    boolean supports(String providerName);
    AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception;

    // Default no-op so providers that don't (yet) support listing available models — i.e.
    // everything except Gemini today — don't need any change. Implementors return the raw
    // model ids that support text generation, e.g. "gemini-2.0-flash".
    default List<String> listModels(String apiKey) throws IOException, URISyntaxException, InterruptedException {
        return List.of();
    }
}