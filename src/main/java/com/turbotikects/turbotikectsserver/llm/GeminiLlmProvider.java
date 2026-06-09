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

@Slf4j
@Component
public class GeminiLlmProvider implements LlmProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("gemini", "Google Gemini", "gemini-2.0-flash");

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) return false;
        String lower = providerName.toLowerCase();
        return lower.contains("gemini") || lower.contains("google");
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException, URISyntaxException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        String url = BASE_URL + "/v1beta/models/" + settings.getModelName() + ":generateContent?key=" + settings.getApiKey();

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
        log.info("Gemini send → {}", response.statusCode());

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
        String url = BASE_URL + "/v1beta/models?key=" + settings.getApiKey();
        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini validateKey → {}", response.statusCode());
        return buildResult(response.statusCode());
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
