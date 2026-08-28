package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.AiSettingsDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.llm.LlmProviderFactory;
import com.turbotikects.turbotikectsserver.repositorys.AiSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AiSettingsService {
    @Autowired
    AiSettingsRepository aiSettingsRepository;

    @Autowired
    LlmProviderFactory llmProviderFactory;

    public List<AiSettingsDto> getAiSettings() {

        List<AiSettingsDto> aiSettings = new ArrayList<>();

       List<AiSettingsEntity> aiSettingsEntityList = aiSettingsRepository.findAll();
        for (AiSettingsEntity aiSettingsEntity : aiSettingsEntityList) {
            AiSettingsDto aiSettingsDto = new AiSettingsDto();
            aiSettingsDto.setId(aiSettingsEntity.getId());
            aiSettingsDto.setModelName(aiSettingsEntity.getModelName());
            aiSettingsDto.setActive(aiSettingsEntity.isActive());
            aiSettingsDto.setSystem(aiSettingsEntity.isSystem());
            aiSettingsDto.setProviderName(aiSettingsEntity.getProviderName());
            aiSettings.add(aiSettingsDto);
        }

        return aiSettings;
    }

    public AiSettingsEntity getActiveAi(){

        List<AiSettingsEntity> aiSettingsEntity  = aiSettingsRepository.findByIsActive(true);

        if(aiSettingsEntity != null && !aiSettingsEntity.isEmpty()){

           return  aiSettingsEntity.get(0);

        }
        return null;


    }

    public void addAiSettings(AiSettingsDto aiSettingsDto){

        AiSettingsEntity aiSettingsEntity = new AiSettingsEntity();
        aiSettingsEntity.setModelName(aiSettingsDto.getModelName());
        aiSettingsEntity.setActive(false);
        aiSettingsEntity.setApiKey(aiSettingsDto.getApiKey());
        aiSettingsEntity.setCreatedAt(LocalDateTime.now());
        aiSettingsEntity.setUpdatedAt(LocalDateTime.now());
        aiSettingsEntity.setProviderName(aiSettingsDto.getProviderName());
        aiSettingsRepository.save(aiSettingsEntity);

    }

    public void deleteIASetting(Long aiSettingID){

        AiSettingsEntity aiSettingsEntity = aiSettingsRepository.findById(aiSettingID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI setting not found"));

        if (aiSettingsEntity.isSystem())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Built-in AI providers cannot be deleted. Activate a different provider to replace it.");

        aiSettingsRepository.delete(aiSettingsEntity);
        aiSettingsRepository.flush();
    }

    public void setActive(Long aiSettingID){
        AiSettingsEntity aiSettingsEntity = new AiSettingsEntity();
        aiSettingsEntity.setId(aiSettingID);

        aiSettingsRepository.deactivateAllSettings();
        aiSettingsRepository.deactivatedSettings(aiSettingID);
        aiSettingsRepository.flush();
    }

    public Map<String, Boolean> getAiStatus() throws URISyntaxException, IOException, InterruptedException {
        AiSettingsEntity active = getActiveAi();

        return Map.of("hasActiveAI", true);

        /* AiSettingsEntity active = getActiveAi();
        boolean hasActiveAI = active != null && aiValidSetting(active);
        return Map.of("hasActiveAI", hasActiveAI);*/
    }

    public AiSettingTestResultDto testIASetting(Long aiSettingID) throws Exception {
        AiSettingsEntity entity = aiSettingsRepository.findById(aiSettingID).orElseThrow();
        return llmProviderFactory.getProvider(entity.getProviderName()).validateKey(entity);
    }

    // Retry a rate-limited call rather than fail it immediately — requested live by a user hitting
    // Gemini's free tier limits on a real, somewhat-large request ("gemini is free with limit
    // tokens, can we help gemini to get the current response even if will take more time"). Every
    // LlmProvider.send() implementation here follows the same established convention of embedding
    // the real HTTP status in the thrown IOException's message (e.g. "Gemini API error 429: ..."),
    // so detecting a rate-limit specifically (not a genuine 400/401/403 the retry can't fix) by
    // inspecting that message is reliable, not a hack — it's the same signal every provider already
    // surfaces uniformly, without needing a new exception type threaded through 7+ provider files.
    private static final int LLM_RETRY_MAX_ATTEMPTS = 4;
    private static final long LLM_RETRY_INITIAL_BACKOFF_MS = 20_000;

    public String sendLlmRequest(AiSettingsEntity aiSettingsEntity, List<LlmStructure> llmRequest) throws URISyntaxException, IOException, InterruptedException {
        return sendLlmRequest(aiSettingsEntity, llmRequest, true);
    }

    /** expectJson=false for free-form conversational calls (e.g. ticket chat) — see
     * LlmProvider.send()'s 3-arg overload for why this exists. */
    public String sendLlmRequest(AiSettingsEntity aiSettingsEntity, List<LlmStructure> llmRequest, boolean expectJson) throws URISyntaxException, IOException, InterruptedException {
        var provider = llmProviderFactory.getProvider(aiSettingsEntity.getProviderName());
        long backoffMs = LLM_RETRY_INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= LLM_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                return provider.send(aiSettingsEntity, llmRequest, expectJson);
            } catch (IOException e) {
                if (attempt == LLM_RETRY_MAX_ATTEMPTS || !isRateLimitError(e)) throw e;
                log.warn("[AiSettingsService] {} rate-limited (attempt {}/{}) — retrying in {}s: {}",
                        aiSettingsEntity.getProviderName(), attempt, LLM_RETRY_MAX_ATTEMPTS, backoffMs / 1000, e.getMessage());
                Thread.sleep(backoffMs);
                backoffMs *= 2;
            }
        }
        throw new IOException("Unreachable"); // loop always returns or throws above
    }

    private boolean isRateLimitError(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return msg.contains(" 429") || msg.contains(" 503")
                || lower.contains("rate limit") || lower.contains("resource_exhausted") || lower.contains("quota");
    }

    public List<String> listModels(String providerName, String apiKey) throws URISyntaxException, IOException, InterruptedException {
        return llmProviderFactory.getProvider(providerName).listModels(apiKey);
    }

    // Real API responses can be large; capped for the same reason ExternalApiActionExecutor caps
    // its own trace/preview fields — bounded prompt size regardless of provider/model.
    private static final int MAX_LLM_EXTRACTION_RESPONSE_CHARS = 200_000;

    /**
     * Per-capture "AI: describe what to extract" mode (ExternalApiActionExecutor's "llm"-mode
     * response captures) — an admin writes a plain-language instruction once, and this is called
     * LIVE every time that capture needs to resolve, both in the wizard's "Verify Captures"/"Test
     * this call now" and at real ticket-execution time. Deliberately much simpler than the design-
     * time JSONPath-discovery prompts in TemplateService (no flatten/compact/path-list scaffolding)
     * — it's answering a question about a real value, not proposing a path a human will review, so
     * the model just reads the real response directly and answers directly. Every failure mode
     * (bad JSON reply, network error, timeout, rate limit exhausted) is caught here and reported as
     * an empty Optional — callers treat that identically to a JsonPath capture that "matched
     * nothing" (a graceful warning, never a crash), so no new exception type needs to thread through
     * ExternalApiActionExecutor.
     */
    public Optional<String> extractValueWithLlm(AiSettingsEntity aiSettings, String responseBody, String instruction) {
        try {
            String capped = responseBody != null && responseBody.length() > MAX_LLM_EXTRACTION_RESPONSE_CHARS
                    ? responseBody.substring(0, MAX_LLM_EXTRACTION_RESPONSE_CHARS) + "…" : responseBody;

            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("""
                    You extract a single value from a JSON (or plain-text) API response, following \
                    the admin's instruction exactly. Return ONLY a strict JSON object of the form \
                    {"value": <the extracted value>} — no markdown, no explanation, no extra keys. \
                    If the value genuinely cannot be found in the response, return {"value": null}. \
                    The value must be a JSON string, number, or boolean — never an object or array.
                    """);

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent("Instruction: " + instruction + "\n\nAPI response:\n" + (capped == null ? "" : capped));

            String raw = sendLlmRequest(aiSettings, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            Map<String, Object> parsed = new ObjectMapper().readValue(cleaned, new TypeReference<>() {});
            Object value = parsed.get("value");
            return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
        } catch (Exception e) {
            log.warn("[AiSettingsService] extractValueWithLlm failed for instruction '{}': {}", instruction, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * "AI Summary" response captures (mcp_tool's Map & Verify Output step — admin selects a whole
     * array/object in the response tree, not a single leaf, since there's no single scalar to
     * JSONPath-extract from a branch). Sibling of {@link #extractValueWithLlm} — same live-every-
     * time-it-resolves contract (design-time verify AND real ticket execution), same graceful-empty-
     * Optional failure mode — but returns an HTML fragment for a "rich-text" field instead of one
     * scalar value.
     * <p>
     * Kept to a small, explicit allow-list of tags in the prompt rather than trusted freely: this
     * HTML is rendered later via RichTextEditor (Tiptap), which only reconstructs node/mark types
     * its configured extensions recognize — an unrecognized tag (script, style, an event-handler
     * attribute) is dropped on render, not executed, but asking for exactly what will render
     * correctly avoids the AI wasting effort on markup that would just be stripped.
     */
    public Optional<String> summarizeAsHtml(AiSettingsEntity aiSettings, Object jsonData, String customInstruction) {
        try {
            String json = new ObjectMapper().writeValueAsString(jsonData);
            String capped = json.length() > MAX_LLM_EXTRACTION_RESPONSE_CHARS
                    ? json.substring(0, MAX_LLM_EXTRACTION_RESPONSE_CHARS) + "…" : json;

            LlmStructure system = new LlmStructure();
            system.setRole("system");
            system.setContent("""
                    You write a short, human-readable HTML summary of a JSON data snippet for a
                    rich-text field on a support ticket. Return ONLY a strict JSON object of the form
                    {"html": "<the HTML fragment>"} — no markdown, no explanation, no extra keys.
                    Rules:
                    - Use only these tags: p, br, ul, ol, li, b, strong, i, em, h3, h4, a. No script,
                      style, iframe, or event-handler attributes (onclick, onload, etc.) — they will
                      never render and are simply wasted effort.
                    - Cover every field actually present in the data — don't skip values.
                    - Write for a human reading a ticket, not a developer — plain language, no raw
                      JSON keys quoted verbatim unless that key IS the natural human label.
                    - If the data is empty or genuinely has nothing to summarize, return
                      {"html": null}.
                    """);

            LlmStructure user = new LlmStructure();
            user.setRole("user");
            user.setContent((customInstruction == null || customInstruction.isBlank() ? "" : "Specific ask: " + customInstruction + "\n\n")
                    + "Data:\n" + capped);

            String raw = sendLlmRequest(aiSettings, List.of(system, user));
            String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            Map<String, Object> parsed = new ObjectMapper().readValue(cleaned, new TypeReference<>() {});
            Object html = parsed.get("html");
            return (html instanceof String s && !s.isBlank()) ? Optional.of(s) : Optional.empty();
        } catch (Exception e) {
            log.warn("[AiSettingsService] summarizeAsHtml failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

}
