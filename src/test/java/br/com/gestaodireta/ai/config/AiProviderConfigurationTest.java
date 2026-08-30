package br.com.gestaodireta.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiProperties;
import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiTextGenerationClient;
import br.com.gestaodireta.ai.infrastructure.ollama.OllamaAiProperties;
import br.com.gestaodireta.ai.infrastructure.ollama.OllamaAiTextGenerationClient;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiProviderConfigurationTest {

    private final AiProviderConfiguration configuration = new AiProviderConfiguration();

    @Test
    void shouldSelectGeminiProvider() {
        AiTextGenerationClient client = clientFor("gemini", "test-key");

        assertThat(client).isInstanceOf(GeminiAiTextGenerationClient.class);
    }

    @Test
    void shouldSelectOllamaProvider() {
        AiTextGenerationClient client = clientFor("ollama", null);

        assertThat(client).isInstanceOf(OllamaAiTextGenerationClient.class);
    }

    @Test
    void shouldRejectUnsupportedProviderAndMissingGeminiApiKey() {
        assertThatThrownBy(() -> clientFor("abc", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported AI provider: abc");
        assertThatThrownBy(() -> clientFor("gemini", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AI_GEMINI_API_KEY");
    }

    private AiTextGenerationClient clientFor(String provider, String geminiApiKey) {
        AiProviderProperties providerProperties = new AiProviderProperties();
        providerProperties.setProvider(provider);
        OllamaAiProperties ollamaProperties = new OllamaAiProperties();
        ollamaProperties.setBaseUrl("http://localhost:11434");
        ollamaProperties.setModel("llama3.2:3b");
        GeminiAiProperties geminiProperties = new GeminiAiProperties();
        geminiProperties.setApiKey(geminiApiKey);
        geminiProperties.setModel("gemini-3.1-flash-lite");
        FinancialExtractionProperties extractionProperties = new FinancialExtractionProperties();
        AiHealthProperties healthProperties = new AiHealthProperties();

        return configuration.aiTextGenerationClient(
                providerProperties,
                ollamaProperties,
                geminiProperties,
                extractionProperties,
                healthProperties,
                RestClient.builder());
    }
}
