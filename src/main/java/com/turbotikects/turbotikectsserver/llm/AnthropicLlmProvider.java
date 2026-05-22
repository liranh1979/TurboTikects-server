package com.turbotikects.turbotikectsserver.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AnthropicLlmProvider implements LlmProvider {

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) return false;
        String lower = providerName.toLowerCase();
        return lower.contains("anthropic") || lower.contains("claude");
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        // Always use the canonical Anthropic messages endpoint — extract just the host from whatever the user stored
        String url = buildAnthropicUrl(settings.getBaseUrl());

        // Anthropic separates the system message from the conversation
        String systemContent = "";
        List<Map<String, String>> conversation = new ArrayList<>();
        for (LlmStructure msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemContent = msg.getContent();
            } else {
                conversation.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", settings.getModelName());
        payload.put("max_tokens", 4096);
        payload.put("messages", conversation);
        if (!systemContent.isEmpty()) {
            payload.put("system", systemContent);
        }

        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Anthropic request to {} | response status {}", url, response.statusCode());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Anthropic API error " + response.statusCode() + ": " + response.body());
        }

        AnthropicResponse anthropicResponse = mapper.readValue(response.body(), AnthropicResponse.class);
        if (anthropicResponse.getContent() != null && !anthropicResponse.getContent().isEmpty()) {
            return anthropicResponse.getContent().get(0).getText();
        }
        return "";
    }

    // Extract just scheme+host from whatever the user stored and always use the canonical path
    private String buildAnthropicUrl(String baseUrl) throws URISyntaxException {
        URI uri = new URI(baseUrl.trim());
        return uri.getScheme() + "://" + uri.getHost() + "/v1/messages";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AnthropicResponse {
        private List<ContentBlock> content;
        public List<ContentBlock> getContent() { return content; }
        public void setContent(List<ContentBlock> content) { this.content = content; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContentBlock {
        private String text;
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}