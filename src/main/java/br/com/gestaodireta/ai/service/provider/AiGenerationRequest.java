package br.com.gestaodireta.ai.service.provider;

import java.util.Map;

public record AiGenerationRequest(
        String prompt, Map<String, Object> responseSchema, Integer maxOutputTokens) {

    public AiGenerationRequest(String prompt, Map<String, Object> responseSchema) {
        this(prompt, responseSchema, null);
    }

    public AiGenerationRequest(String prompt) {
        this(prompt, null);
    }

    public boolean hasResponseSchema() {
        return responseSchema != null && !responseSchema.isEmpty();
    }
}
