package com.turbotikects.turbotikectsserver.llm;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmProviderFactory {

    private final List<LlmProvider> providers;

    public LlmProviderFactory(List<LlmProvider> providers) {
        this.providers = providers;
    }

    public LlmProvider getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.supports(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No LLM provider found for: " + providerName));
    }
}