package com.turbotikects.turbotikectsserver.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Local model served by Ollama (no API key, no per-token cost) — the pre-built,
// always-available provider. See docker/docker-compose.yml for the "ollama" service.
@Slf4j
@Component
public class GemmaLlmProvider implements LlmProvider {

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("gemma", "Gemma 4 (Local, Ollama)", "gemma4:e2b");

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Override
    public boolean supports(String providerName) {
        return "gemma".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        List<Map<String, String>> chatMessages = messages.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        Map<String, Object> payload = Map.of(
                "model", settings.getModelName(),
                "messages", chatMessages,
                "stream", false
        );

        HttpRequest request = HttpRequest.newBuilder(new URI(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemma (Ollama) send → {}", response.statusCode());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama API error " + response.statusCode() + ": " + response.body());
        }

        OllamaChatResponse parsed = mapper.readValue(response.body(), OllamaChatResponse.class);
        return parsed.getMessage() != null ? parsed.getMessage().getContent() : "";
    }

    @Override
    public AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(new URI(baseUrl + "/api/tags")).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemma (Ollama) validateKey → {}", response.statusCode());

        AiSettingTestResultDto result = new AiSettingTestResultDto();
        if (response.statusCode() != 200) {
            result.setSuccess(false);
            result.setMessage("Ollama not reachable (HTTP " + response.statusCode() + ")");
            return result;
        }

        ObjectMapper mapper = new ObjectMapper();
        OllamaTagsResponse tags = mapper.readValue(response.body(), OllamaTagsResponse.class);
        boolean modelPulled = tags.getModels() != null && tags.getModels().stream()
                .anyMatch(m -> settings.getModelName().equals(m.getName()));

        if (modelPulled) {
            result.setSuccess(true);
            result.setMessage("Connected");
        } else {
            result.setSuccess(false);
            result.setMessage("Ollama is reachable but " + settings.getModelName() + " is not pulled yet");
        }
        return result;
    }

    @Override
    public List<String> listModels(String apiKey) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(new URI(baseUrl + "/api/tags")).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemma (Ollama) listModels → {}", response.statusCode());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama API error " + response.statusCode() + ": " + response.body());
        }
        ObjectMapper mapper = new ObjectMapper();
        OllamaTagsResponse tags = mapper.readValue(response.body(), OllamaTagsResponse.class);
        if (tags.getModels() == null) return List.of();
        return tags.getModels().stream().map(OllamaModel::getName).sorted().collect(Collectors.toList());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaChatResponse {
        private OllamaMessage message;
        public OllamaMessage getMessage() { return message; }
        public void setMessage(OllamaMessage message) { this.message = message; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaMessage {
        private String role;
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public void setRole(String role) { this.role = role; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaTagsResponse {
        private List<OllamaModel> models;
        public List<OllamaModel> getModels() { return models; }
        public void setModels(List<OllamaModel> models) { this.models = models; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaModel {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
