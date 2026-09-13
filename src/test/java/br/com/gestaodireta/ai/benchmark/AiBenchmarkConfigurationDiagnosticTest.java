package br.com.gestaodireta.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiProperties;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiBenchmarkConfigurationDiagnosticTest {

    @Test
    void shouldDescribeGeminiConfigurationWithoutExposingApiKey() {
        GeminiAiProperties geminiProperties = new GeminiAiProperties();
        geminiProperties.setApiKey("secret-gemini-api-key");
        geminiProperties.setModel("gemini-3.1-flash-lite");

        String diagnostic =
                AiBenchmarkConfigurationDiagnostic.describe(
                        "gemini",
                        new StubGeminiClient(),
                        geminiProperties,
                        geminiProperties.getModel(),
                        new MockEnvironment().withProperty("spring.profiles.active", "test"));

        assertThat(diagnostic)
                .contains(
                        "requestedProvider=gemini",
                        "providerBean=StubGeminiClient",
                        "geminiApiKeyConfigured=true",
                        "model=gemini-3.1-flash-lite")
                .doesNotContain("secret-gemini-api-key");
    }

    private static final class StubGeminiClient implements AiTextGenerationClient {

        @Override
        public String generate(AiGenerationRequest request) {
            return "{}";
        }

        @Override
        public String providerName() {
            return "gemini";
        }

        @Override
        public String modelName() {
            return "gemini-3.1-flash-lite";
        }
    }
}
