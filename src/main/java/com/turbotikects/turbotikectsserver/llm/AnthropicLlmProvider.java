package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
@Component
public class AnthropicLlmProvider implements LlmProvider {

    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODELS_URL   = "https://api.anthropic.com/v1/models";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("anthropic", "Anthropic (Claude)", "claude-opus-4-7");

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) return false;
        String lower = providerName.toLowerCase();
        return lower.contains("anthropic") || lower.contains("claude");
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException {
        try {
            // The old hand-written system/user-role-splitting loop is gone — LangChain4jSupport.
            // toChatMessages + AnthropicChatModel handle a SystemMessage in the list correctly on
            // their own, no separate "system" field needed.
            ChatModel model = AnthropicChatModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(settings.getModelName())
                    .build();
            ChatResponse response = model.chat(LangChain4jSupport.toChatMessages(messages));
            return LangChain4jSupport.extractText(response);
        } catch (LangChain4jException e) {
            throw LangChain4jSupport.toIOException("Anthropic", e);
        }
    }

    @Override
    public AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(new URI(MODELS_URL))
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Anthropic validateKey → {}", response.statusCode());
        return buildResult(response.statusCode());
    }

    private AiSettingTestResultDto buildResult(int status) {
        AiSettingTestResultDto result = new AiSettingTestResultDto();
        if (status == 200) {
            result.setSuccess(true);
            result.setMessage("Connected");
        } else if (status == 401 || status == 403) {
            result.setSuccess(false);
            result.setMessage("Invalid API key");
        } else {
            result.setSuccess(false);
            result.setMessage("HTTP " + status);
        }
        return result;
    }
}
