package com.turbotikects.turbotikectsserver.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeminiLlmProvider implements LlmProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("gemini", "Google Gemini (Beta)", "gemini-2.0-flash");

    // Exact match — kept this strict (not "contains gemini/google") so it can't also claim
    // requests meant for GeminiStableLlmProvider's distinct "gemini_stable" provider name.
    @Override
    public boolean supports(String providerName) {
        return "gemini".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException {
        try {
            // GoogleAiGeminiChatModel (1.0.0-beta5) has no baseUrl override — it always calls the
            // same /v1beta surface this class already hit, so behavior is unchanged.
            // ResponseFormat.JSON forces Gemini's own JSON-mode constrained decoding — see
            // GemmaLlmProvider's identical fix for the fuller reasoning.
            ChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(settings.getModelName())
                    .responseFormat(ResponseFormat.JSON)
                    .build();
            ChatResponse response = model.chat(LangChain4jSupport.toChatMessages(messages));
            return LangChain4jSupport.extractText(response);
        } catch (LangChain4jException e) {
            throw LangChain4jSupport.toIOException("Gemini", e);
        }
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

    @Override
    public List<String> listModels(String apiKey) throws IOException, URISyntaxException, InterruptedException {
        String url = BASE_URL + "/v1beta/models?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini listModels → {}", response.statusCode());
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
}
