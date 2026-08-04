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
import dev.langchain4j.model.ollama.OllamaChatModel;
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

    // A real bug found live: this app has several periodic background schedulers (dashboard AI
    // insights, SLA breach-risk scoring, etc.) that call the currently-active AI provider on their
    // own cadence (as often as every ~10-60s) — when Gemma is active, every one of those calls
    // shares this single local Ollama instance. Overlapping/concurrent calls to the same model on
    // one Ollama server were observed live to silently corrupt each other's output (confirmed via
    // repeated real failures always coinciding, within ~1s, with another scheduler's own Gemma
    // call — output degraded across attempts: a truncated word, a single stray "{", then
    // completely empty — despite each individual HTTP call still returning 200). A single lock
    // around every call this class makes serializes them — concurrent callers queue instead of
    // racing, at the cost of some added latency when the AI is in high demand, which is a much
    // better tradeoff than silently-wrong/empty output. Scoped to this provider only (a local,
    // single-process server with no concurrency guarantees of its own) — cloud providers
    // (Gemini/Anthropic/OpenAI/etc.) handle their own internal concurrency and don't need this.
    private static final Object OLLAMA_CALL_LOCK = new Object();

    @Override
    public boolean supports(String providerName) {
        return "gemma".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException {
        synchronized (OLLAMA_CALL_LOCK) {
            try {
                // A real root cause found live (this explained empty/truncated output far better
                // than an earlier concurrency theory did — GET /api/ps showed this model actually
                // loaded with "context_length": 4096, Ollama's own default for it): a real-world
                // prompt for a structured-extraction task (system rules + a real API response's
                // flattened field list) can easily approach or exceed 4096 tokens on its own,
                // leaving little or no room in the SAME window for the model to generate its JSON
                // reply — which looks exactly like "the model returned almost nothing", because it
                // effectively had nowhere left to write. Explicitly requesting a larger context via
                // numCtx fixes this at the call level without needing to edit the model's own
                // Ollama configuration. Larger context uses more RAM (or VRAM now that this
                // container runs on GPU — see docker-compose.yml's nvidia device reservation), so
                // this is a reasonable, low-risk tradeoff for correctness either way.
                //
                // A separate, distinct problem from context size: every AI method in TemplateService
                // asks for "ONLY the JSON" in prose and hopes the model complies — a real, GPU speed
                // has NO bearing on this (faster tokens/sec doesn't make a small model more
                // grammatically disciplined). ".format(\"json\")" is Ollama's own grammar-constrained
                // decoding mode — the token sampler itself is restricted so it can ONLY ever emit
                // syntactically valid JSON, eliminating "The AI did not return valid JSON" (a parse
                // failure on genuinely malformed output, e.g. trailing prose or an unclosed brace) as
                // a failure mode entirely. It does NOT guarantee the JSON matches our expected shape
                // (still just as capable of, say, an empty array) — that's the sanitize*/MANDATORY
                // CHECK prompt rules' job, unchanged — but the specific "isn't even valid JSON" class
                // of error this fixes is exactly the one reported live.
                ChatModel model = OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(settings.getModelName())
                        .numCtx(16384)
                        .responseFormat(ResponseFormat.JSON)
                        .build();
                ChatResponse response = model.chat(LangChain4jSupport.toChatMessages(messages));
                return LangChain4jSupport.extractText(response);
            } catch (LangChain4jException e) {
                throw LangChain4jSupport.toIOException("Ollama", e);
            }
        }
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
