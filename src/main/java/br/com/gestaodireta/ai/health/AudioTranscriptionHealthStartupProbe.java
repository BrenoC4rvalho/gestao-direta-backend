package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AudioTranscriptionHealthStartupProbe {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AudioTranscriptionHealthStartupProbe.class);

    private final AudioTranscriptionHealthCheckService healthCheckService;
    private final AudioTranscriptionClient client;

    public AudioTranscriptionHealthStartupProbe(
            AudioTranscriptionHealthCheckService healthCheckService,
            AudioTranscriptionClient client) {
        this.healthCheckService = healthCheckService;
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        AudioTranscriptionHealthCheckResult result = healthCheckService.check();
        if (result.up()) {
            LOGGER.info(
                    "Audio transcription provider initialized: provider={} model={} status=UP latencyMs={}",
                    client.providerName(),
                    client.modelName(),
                    result.latencyMs());
            return;
        }
        LOGGER.warn(
                "Audio transcription provider initialized: provider={} model={} status=DOWN reason={} latencyMs={}",
                client.providerName(),
                client.modelName(),
                result.errorType(),
                result.latencyMs());
    }
}
