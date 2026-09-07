package br.com.gestaodireta.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiProperties;
import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiTextGenerationClient;
import br.com.gestaodireta.ai.infrastructure.gemini.GeminiMultimodalAudioTranscriptionClient;
import br.com.gestaodireta.ai.infrastructure.ollama.OllamaAiProperties;
import br.com.gestaodireta.ai.infrastructure.ollama.OllamaAiTextGenerationClient;
import br.com.gestaodireta.ai.infrastructure.whisper.WhisperAudioTranscriptionClient;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProvider;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProviderProperties;
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

    @Test
    void shouldSelectGeminiMultimodalAudioTranscriptionClient() {
        assertThat(audioClient()).isInstanceOf(GeminiMultimodalAudioTranscriptionClient.class);
    }

    @Test
    void shouldSelectWhisperAudioTranscriptionClientWithoutGeminiApiKey() {
        AudioTranscriptionProperties transcriptionProperties = new AudioTranscriptionProperties();
        transcriptionProperties.setEnabled(true);
        AudioTranscriptionProviderProperties providerProperties =
                new AudioTranscriptionProviderProperties();
        providerProperties.setProvider(AudioTranscriptionProvider.WHISPER);

        assertThat(
                        configuration.audioTranscriptionClient(
                                transcriptionProperties,
                                providerProperties,
                                new GeminiAiProperties(),
                                RestClient.builder()))
                .isInstanceOf(WhisperAudioTranscriptionClient.class);
    }

    @Test
    void shouldUseDisabledClientWhenAudioTranscriptionIsDisabled() {
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();

        assertThat(
                        configuration.audioTranscriptionClient(
                                properties,
                                new AudioTranscriptionProviderProperties(),
                                new GeminiAiProperties(),
                                RestClient.builder()))
                .isInstanceOf(
                        br.com.gestaodireta.ai.transcription.DisabledAudioTranscriptionClient
                                .class);
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

    private AudioTranscriptionClient audioClient() {
        AudioTranscriptionProperties transcriptionProperties = new AudioTranscriptionProperties();
        transcriptionProperties.setEnabled(true);
        GeminiAiProperties geminiProperties = new GeminiAiProperties();
        geminiProperties.setApiKey("test-key");
        return configuration.audioTranscriptionClient(
                transcriptionProperties,
                new AudioTranscriptionProviderProperties(),
                geminiProperties,
                RestClient.builder());
    }
}
