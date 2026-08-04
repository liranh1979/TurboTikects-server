package com.turbotikects.turbotikectsserver.llm;

import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared LlmStructure&lt;-&gt;LangChain4j ChatMessage conversion + exception normalization, reused
 * identically by every LlmProvider migrated onto LangChain4j (replacing hand-rolled
 * java.net.http.HttpClient calls) — see the individual provider classes' send() methods.
 *
 * The normalization in {@link #toIOException} exists for one reason: AiSettingsService.
 * sendLlmRequest's retry-on-rate-limit loop decides whether to retry by string-matching the
 * thrown IOException's message for " 429"/" 503" (space-prefixed, a literal substring check, not
 * a regex) or the lowercase substrings "rate limit"/"resource_exhausted"/"quota" — a convention
 * every hand-rolled provider already followed via "&lt;Provider&gt; API error &lt;code&gt;: &lt;detail&gt;".
 * LangChain4j throws its own unchecked exception hierarchy instead of a plain IOException, so every
 * migrated provider must catch it here and re-wrap it in a way that still satisfies that same
 * substring check, or the retry silently stops working.
 */
final class LangChain4jSupport {

    private LangChain4jSupport() {}

    static List<ChatMessage> toChatMessages(List<LlmStructure> messages) {
        return messages.stream().<ChatMessage>map(m -> switch (m.getRole()) {
            case "system" -> new SystemMessage(m.getContent());
            case "assistant" -> new AiMessage(m.getContent());
            default -> new UserMessage(m.getContent()); // "user" and any unrecognized role
        }).collect(Collectors.toList());
    }

    static String extractText(ChatResponse response) {
        return response.aiMessage() != null && response.aiMessage().text() != null
                ? response.aiMessage().text() : "";
    }

    /**
     * HttpException carries a real numeric status (429/500/503/etc.) — preserved verbatim so
     * isRateLimitError's " 429"/" 503" checks keep matching exactly as they did against the old
     * hand-rolled providers' identical "API error &lt;code&gt;" phrasing. RateLimitException (thrown by
     * some providers with no HTTP status attached) has no numeric code to give, but is
     * unambiguously a rate limit — the literal text "rate limit" is included so the lowercase
     * substring check still catches it. Anything else becomes a plain, non-retriable IOException,
     * matching how a non-rate-limit failure already behaved (rethrown immediately, no retry).
     */
    static IOException toIOException(String providerLabel, LangChain4jException e) {
        String detail = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof HttpException http) {
            return new IOException(providerLabel + " API error " + http.statusCode() + ": " + detail, e);
        }
        if (e instanceof RateLimitException) {
            return new IOException(providerLabel + " API error (rate limit): " + detail, e);
        }
        return new IOException(providerLabel + " API error: " + detail, e);
    }
}
