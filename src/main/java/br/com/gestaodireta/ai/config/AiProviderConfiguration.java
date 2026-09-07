package br.com.gestaodireta.ai.config;

import br.com.gestaodireta.ai.infrastructure.fake.FakeAiTextGenerationClient;
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
import br.com.gestaodireta.ai.transcription.DisabledAudioTranscriptionClient;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiProviderConfiguration {

    @Bean
    public AudioTranscriptionClient audioTranscriptionClient(
            AudioTranscriptionProperties transcriptionProperties,
            AudioTranscriptionProviderProperties providerProperties,
            GeminiAiProperties geminiProperties,
            RestClient.Builder restClientBuilder) {
        if (!transcriptionProperties.isEnabled()) {
            return new DisabledAudioTranscriptionClient();
        }
        AudioTranscriptionProvider provider = providerProperties.getProvider();
        if (provider == AudioTranscriptionProvider.GEMINI) {
            validateGeminiApiKey(geminiProperties.getApiKey());
            return new GeminiMultimodalAudioTranscriptionClient(
                    restClientBuilder, geminiProperties, transcriptionProperties, true);
        }
        if (provider == AudioTranscriptionProvider.WHISPER) {
            return new WhisperAudioTranscriptionClient(
                    restClientBuilder, providerProperties.getWhisper());
        }
        throw new IllegalStateException("Unsupported audio transcription provider: " + provider);
    }

    @Bean
    public AiTextGenerationClient aiTextGenerationClient(
            AiProviderProperties providerProperties,
            OllamaAiProperties ollamaProperties,
            GeminiAiProperties geminiProperties,
            FinancialExtractionProperties extractionProperties,
            AiHealthProperties healthProperties,
            RestClient.Builder restClientBuilder) {
        String provider = normalize(providerProperties.getProvider());

        return switch (provider) {
            case "ollama" ->
                    new OllamaAiTextGenerationClient(
                            restClientBuilder,
                            ollamaProperties,
                            extractionProperties.getTimeoutSeconds(),
                            healthProperties.getTimeoutSeconds());
            case "gemini" -> {
                validateGeminiApiKey(geminiProperties.getApiKey());
                yield new GeminiAiTextGenerationClient(
                        restClientBuilder,
                        geminiProperties,
                        extractionProperties.getTimeoutSeconds(),
                        healthProperties.getTimeoutSeconds());
            }
            case "fake" -> new FakeAiTextGenerationClient();
            default -> throw new IllegalStateException("Unsupported AI provider: " + provider);
        };
    }

    private String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private void validateGeminiApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "APP_AI_GEMINI_API_KEY must be configured when Gemini is enabled");
        }
    }
}
