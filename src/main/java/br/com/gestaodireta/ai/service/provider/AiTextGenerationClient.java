package br.com.gestaodireta.ai.service.provider;

public interface AiTextGenerationClient {

    String generate(AiGenerationRequest request);

    String providerName();

    default String modelName() {
        return providerName();
    }
}
