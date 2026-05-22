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
public class GeminiLlmProvider implements LlmProvider {

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) return false;
        String lower = providerName.toLowerCase();
        return lower.contains("gemini") || lower.contains("google");
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        // Gemini URL: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
        String url = buildGeminiUrl(settings);

        // Gemini separates system instruction from contents; role is "user" or "model"
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", contents);
        if (!systemContent.isEmpty()) {
            payload.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemContent))));
        }

        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini request to {} | response status {}", url, response.statusCode());

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

    // Always build the canonical Gemini URL, ignoring whatever path the user may have stored in baseUrl
    private String buildGeminiUrl(AiSettingsEntity settings) {
        String base = settings.getBaseUrl().trim();
        // Strip everything after /v1beta to get a clean root, then rebuild the standard path
        int v1betaIdx = base.indexOf("/v1beta");
        String root = v1betaIdx >= 0 ? base.substring(0, v1betaIdx) : base.replaceAll("/+$", "");
        return root + "/v1beta/models/" + settings.getModelName() + ":generateContent?key=" + settings.getApiKey();
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