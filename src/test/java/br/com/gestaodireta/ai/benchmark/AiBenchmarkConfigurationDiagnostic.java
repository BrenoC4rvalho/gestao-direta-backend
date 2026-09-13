package br.com.gestaodireta.ai.benchmark;

import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiProperties;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import org.springframework.core.env.Environment;

final class AiBenchmarkConfigurationDiagnostic {

    private AiBenchmarkConfigurationDiagnostic() {}

    static String describe(
            String provider,
            AiTextGenerationClient client,
            GeminiAiProperties geminiProperties,
            String model,
            Environment environment) {
        boolean geminiApiKeyConfigured =
                geminiProperties.getApiKey() != null && !geminiProperties.getApiKey().isBlank();
        return "AI benchmark configuration: requestedProvider="
                + provider
                + " providerBean="
                + client.getClass().getSimpleName()
                + " geminiApiKeyConfigured="
                + geminiApiKeyConfigured
                + " model="
                + model
                + " profiles="
                + String.join(",", environment.getActiveProfiles());
    }
}
