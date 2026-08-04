package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.AiSettingTestResultDto;
import com.turbotikects.turbotikectsserver.dto.LlmProviderInfoDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
@Component
public class OpenAiLlmProvider implements LlmProvider {

    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODELS_URL      = "https://api.openai.com/v1/models";

    public static final LlmProviderInfoDto INFO =
            new LlmProviderInfoDto("openai", "OpenAI", "gpt-4o");

    @Override
    public boolean supports(String providerName) {
        if (providerName == null) return true;
        return "openai".equalsIgnoreCase(providerName);
    }

    @Override
    public String send(AiSettingsEntity settings, List<LlmStructure> messages) throws IOException {
        try {
            // "json_object" forces OpenAI's own JSON-mode constrained decoding — every AI method
            // in TemplateService asks for "ONLY the JSON" in the prompt text and hopes the model
            // complies; this enforces it at the API level instead, eliminating "The AI did not
            // return valid JSON" (a parse failure on malformed output) as a possible outcome. See
            // GemmaLlmProvider's identical fix for the fuller reasoning (this app's default active
            // provider, where the failure was actually reported live).
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(settings.getModelName())
                    .temperature(0.3)
                    .responseFormat("json_object")
                    .build();
            ChatResponse response = model.chat(LangChain4jSupport.toChatMessages(messages));
            return LangChain4jSupport.extractText(response);
        } catch (LangChain4jException e) {
            throw LangChain4jSupport.toIOException("OpenAI", e);
        }
    }

    @Override
    public AiSettingTestResultDto validateKey(AiSettingsEntity settings) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(new URI(MODELS_URL))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("OpenAI validateKey → {}", response.statusCode());
        return buildResult(response.statusCode());
    }

    private AiSettingTestResultDto buildResult(int status) {
        AiSettingTestResultDto result = new AiSettingTestResultDto();
        if (status == 200) {
            result.setSuccess(true);
            result.setMessage("Connected");
        } else if (status == 401 || status == 403) {
            result.setSuccess(false);
            result.setMessage("Invalid API key");
        } else {
            result.setSuccess(false);
            result.setMessage("HTTP " + status);
        }
        return result;
    }
}
