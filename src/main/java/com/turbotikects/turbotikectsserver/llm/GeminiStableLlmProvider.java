package com.turbotikects.turbotikectsserver.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
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
import java.util.stream.Collectors;

// Same Gemini API as GeminiLlmProvider, but built against the stable "/v1/" path instead of
// "/v1beta/". Google has been retiring older models (e.g. gemini-1.5-pro) from the beta surface
// faster than the stable one, so this is offered as a separate provider choice rather than
// silently rewriting the existing "gemini" provider's URL.
@Slf4j
@Component
public class GeminiStableLlmProvider implements LlmProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("gemini_stable", "Google Gemini (Stable)", "gemini-2.0-flash");

    @Override
    public boolean supports(String providerName) {
        return "gemini_stable".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        String url = BASE_URL + "/v1/models/" + settings.getModelName() + ":generateContent?key=" + settings.getApiKey();

        String systemContent = "";
        List<Map<String, Object>> contents = new ArrayList<>();
        for (LlmStructure msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemContent = msg.getContent();
            } else {
                String geminiRole = "assistant".equals(msg.getRole()) ? "model" : msg.getRole();
                contents.add(Map.of(
                        "role", geminiRole,
                        "parts", List.of(Map.of("text", msg.getContent()))
                ));
            }
        }

        // The stable "/v1/" surface rejects "systemInstruction" on some models — observed
        // 400 "Unknown name systemInstruction: Cannot find field" — so fold the system prompt
        // into the first turn's text instead of using the dedicated field GeminiLlmProvider
        // (beta) relies on. This works on every model, old or new.
        if (!systemContent.isEmpty()) {
            if (contents.isEmpty()) {
                contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", systemContent))));
            } else {
                Map<String, Object> first = contents.get(0);
                @SuppressWarnings("unchecked")
                String firstText = ((List<Map<String, String>>) first.get("parts")).get(0).get("text");
                contents.set(0, Map.of(
                        "role", first.get("role"),
                        "parts", List.of(Map.of("text", systemContent + "\n\n" + firstText))
                ));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", contents);

        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini (stable) send → {}", response.statusCode());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        GeminiResponse geminiResponse = mapper.readValue(response.body(), GeminiResponse.class);
        if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
            List<Map<String, String>> parts = geminiResponse.getCandidates().get(0).getContent().getParts();
            if (parts != null && !parts.isEmpty()) {
                return parts.get(0).get("text");
            }
        }
        return "";
    }

    @Override
    public AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception {
        String url = BASE_URL + "/v1/models?key=" + settings.getApiKey();
        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini (stable) validateKey → {}", response.statusCode());
        return buildResult(response.statusCode());
    }

    @Override
    public List<String> listModels(String apiKey) throws IOException, URISyntaxException, InterruptedException {
        String url = BASE_URL + "/v1/models?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini (stable) listModels → {}", response.statusCode());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }
        ObjectMapper mapper = new ObjectMapper();
        ModelsListResponse parsed = mapper.readValue(response.body(), ModelsListResponse.class);
        if (parsed.getModels() == null) return List.of();
        return parsed.getModels().stream()
                .filter(m -> m.getSupportedGenerationMethods() != null && m.getSupportedGenerationMethods().contains("generateContent"))
                .map(m -> m.getName().startsWith("models/") ? m.getName().substring("models/".length()) : m.getName())
                .sorted()
                .collect(Collectors.toList());
    }

    private AiSettingTestResultDto buildResult(int status) {
        AiSettingTestResultDto result = new AiSettingTestResultDto();
        if (status == 200) {
            result.setSuccess(true);
            result.setMessage("Connected");
        } else if (status == 400 || status == 403) {
            result.setSuccess(false);
            result.setMessage("Invalid API key");
        } else {
            result.setSuccess(false);
            result.setMessage("HTTP " + status);
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ModelsListResponse {
        private List<ModelInfo> models;
        public List<ModelInfo> getModels() { return models; }
        public void setModels(List<ModelInfo> models) { this.models = models; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ModelInfo {
        private String name;
        private List<String> supportedGenerationMethods;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getSupportedGenerationMethods() { return supportedGenerationMethods; }
        public void setSupportedGenerationMethods(List<String> m) { this.supportedGenerationMethods = m; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiResponse {
        private List<Candidate> candidates;
        public List<Candidate> getCandidates() { return candidates; }
        public void setCandidates(List<Candidate> candidates) { this.candidates = candidates; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Candidate {
        private Content content;
        public Content getContent() { return content; }
        public void setContent(Content content) { this.content = content; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Content {
        private List<Map<String, String>> parts;
        public List<Map<String, String>> getParts() { return parts; }
        public void setParts(List<Map<String, String>> parts) { this.parts = parts; }
    }
}
