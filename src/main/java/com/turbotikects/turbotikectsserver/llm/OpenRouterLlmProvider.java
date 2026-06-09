package com.turbotikects.turbotikectsserver.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmResponse;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenRouterLlmProvider implements LlmProvider {

    private static final String COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODELS_URL      = "https://openrouter.ai/api/v1/models";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("openrouter", "OpenRouter", "openai/gpt-4o");

    @Override
    public boolean supports(String providerName) {
        return "openrouter".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", settings.getModelName());
        payload.put("messages", messages);
        payload.put("temperature", 0.3);

        HttpRequest request = HttpRequest.newBuilder(new URI(COMPLETIONS_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("HTTP-Referer", "https://turbotikects.app")
                .header("X-Title", "TurboTikects")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("OpenRouter send → {}", response.statusCode());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("OpenRouter API error " + response.statusCode() + ": " + response.body());
        }

        LlmResponse llmResponse = mapper.readValue(response.body(), LlmResponse.class);
        if (llmResponse.getChoices() != null && !llmResponse.getChoices().isEmpty()) {
            return llmResponse.getChoices().get(0).getMessage().getContent();
        }
        return "";
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
