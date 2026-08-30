package br.com.gestaodireta.ai.service.provider;

import java.util.Map;

public record AiGenerationRequest(String prompt, Map<String, Object> responseSchema) {

    public AiGenerationRequest(String prompt) {
        this(prompt, null);
    }

    public boolean hasResponseSchema() {
        return responseSchema != null && !responseSchema.isEmpty();
    }
}
