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
        return send(settings, messages, true);
    }

    // gemma4:e2b's real trained context_length (confirmed live via GET /api/show → model_info) is
    // 131072 — the ceiling numCtx should never exceed regardless of how large a prompt gets.
    private static final int MAX_NUM_CTX = 131_072;
    // Floor for typical small calls (ticket chat replies, single-capture extraction, field
    // mapping) — matches this constant's previous fixed value, kept as the minimum so those
    // unaffected call sites see no behavior change.
    private static final int MIN_NUM_CTX = 16_384;
    // Reserved headroom for the model's own reply, on top of whatever the input needs.
    private static final int NUM_CTX_OUTPUT_RESERVE = 4096;

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages, boolean expectJson) throws IOException {
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
                // Ollama configuration.
                //
                // A second, more severe real incident found live (FEAT-19's MCP-wrapper "Analyze"
                // step, driven by a fetched real API doc page — serpapi.com/google-flights-api,
                // ~54.6KB / ~18,600 tokens after Jsoup cleanup): a fixed numCtx(16384) SILENTLY
                // truncates the prompt rather than erroring — verified live via GET /api/ps and
                // comparing prompt_eval_count across identical calls: at numCtx=16384 Ollama only
                // evaluated ~950-1100 tokens of a ~19,000-token prompt (the vast majority of the doc
                // never reached the model at all), while numCtx=65536 correctly evaluated all
                // ~18,600+ — the model then visibly fabricated a generic, textbook-shaped "flights
                // API" (invented endpoints like "/v1/flights/search", never once using the doc's
                // real parameter names departure_id/arrival_id) instead of erroring, which looks
                // exactly like "the AI is ignoring the documentation" rather than a context-size bug.
                // A single global fixed value can't serve both cases well — bumping it enough for a
                // full doc page would waste RAM/VRAM on every small call this provider also handles
                // (ticket chat, single-field extraction, etc.) — so numCtx is now sized dynamically
                // per call from the actual combined message length (chars/3 is a deliberately
                // conservative, token-count-OVERestimating ratio — safer to over-reserve context
                // than under-reserve and silently truncate again), clamped to [16384, 131072] (this
                // model's real trained ceiling, confirmed via GET /api/show's model_info). Verified
                // live: this formula applied to the real 54.6KB doc call above requests numCtx≈23,071
                // and Ollama then evaluates the full ~18,600-token prompt with no truncation.
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
                int estimatedInputChars = messages.stream().mapToInt(m -> m.getContent() == null ? 0 : m.getContent().length()).sum();
                int estimatedInputTokens = estimatedInputChars / 3;
                int numCtx = Math.min(MAX_NUM_CTX, Math.max(MIN_NUM_CTX, estimatedInputTokens + NUM_CTX_OUTPUT_RESERVE));

                var builder = OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(settings.getModelName())
                        .numCtx(numCtx);
                if (expectJson) builder.responseFormat(ResponseFormat.JSON);
                ChatModel model = builder.build();
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
