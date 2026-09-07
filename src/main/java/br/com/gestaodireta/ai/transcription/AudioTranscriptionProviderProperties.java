package br.com.gestaodireta.ai.transcription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.transcription")
public class AudioTranscriptionProviderProperties {

    @NotNull private AudioTranscriptionProvider provider = AudioTranscriptionProvider.GEMINI;

    @Valid private Whisper whisper = new Whisper();

    public AudioTranscriptionProvider getProvider() {
        return provider;
    }

    public void setProvider(AudioTranscriptionProvider value) {
        provider = value;
    }

    public Whisper getWhisper() {
        return whisper;
    }

    public void setWhisper(Whisper value) {
        whisper = value;
    }

    public static class Whisper {

        @NotNull private String baseUrl = "http://localhost:8090";

        @NotNull private Duration timeout = Duration.ofSeconds(120);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String value) {
            baseUrl = value;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            timeout = value;
        }
    }
}
