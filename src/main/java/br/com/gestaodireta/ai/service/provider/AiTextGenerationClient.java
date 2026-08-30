package br.com.gestaodireta.ai.service.provider;

public interface AiTextGenerationClient extends AiProviderHealthProbe {

    String generate(AiGenerationRequest request);

    String providerName();

    @Override
    default void probe() {}

    default String modelName() {
        return providerName();
    }
}
