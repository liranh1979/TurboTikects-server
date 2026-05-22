package com.turbotikects.turbotikectsserver.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class OpenAiLlmProvider implements LlmProvider {

    private static final List<String> SUPPORTED = List.of("openai", "ollama", "lmstudio", "lm-studio", "local");

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) return true; // default fallback
        return SUPPORTED.contains(providerName.toLowerCase());
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        String url = normalizeBaseUrl(settings.getBaseUrl()) + "completions";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", settings.getModelName());
        payload.put("messages", messages);
        payload.put("temperature", "0.3");

        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("OpenAI request to {} | response status {}", url, response.statusCode());
        log.info(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("OpenAI API error " + response.statusCode() + ": " + response.body());
        }

        LlmResponse llmResponse = mapper.readValue(response.body(), LlmResponse.class);
        if (llmResponse.getChoices() != null && !llmResponse.getChoices().isEmpty()) {
            return llmResponse.getChoices().get(0).getMessage().getContent();
        }
        return "";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl.trim();
        return url.endsWith("/") ? url : url + "/";
    }
}