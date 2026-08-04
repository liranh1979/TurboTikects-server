package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenRouterLlmProvider implements LlmProvider {

    private static final String BASE_URL   = "https://openrouter.ai/api/v1";
    private static final String MODELS_URL = "https://openrouter.ai/api/v1/models";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("openrouter", "OpenRouter", "openai/gpt-4o");

    @Override
    public boolean supports(String providerName) {
        return "openrouter".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException {
        try {
            // OpenRouter's API is OpenAI-compatible — reuses langchain4j-open-ai's OpenAiChatModel
            // with OpenRouter's own baseUrl + the same HTTP-Referer/X-Title headers it already sent.
            ChatModel model = OpenAiChatModel.builder()
                    .baseUrl(BASE_URL)
                    .apiKey(settings.getApiKey())
                    .modelName(settings.getModelName())
                    .temperature(0.3)
                    .customHeaders(Map.of("HTTP-Referer", "https://turbotikects.app", "X-Title", "TurboTikects"))
                    .build();
            ChatResponse response = model.chat(LangChain4jSupport.toChatMessages(messages));
            return LangChain4jSupport.extractText(response);
        } catch (LangChain4jException e) {
            throw LangChain4jSupport.toIOException("OpenRouter", e);
        }
    }

    @Override
    public AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(new URI(MODELS_URL))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("HTTP-Referer", "https://turbotikects.app")
                .header("X-Title", "TurboTikects")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("OpenRouter validateKey → {}", response.statusCode());
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
