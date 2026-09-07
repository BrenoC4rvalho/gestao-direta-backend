package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.config.AiHealthProperties;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

    private final AiHealthCheckService healthCheckService;

    private final AiTextGenerationClient client;

    private final AiHealthProperties properties;

    private final AudioTranscriptionHealthCheckService transcriptionHealthCheckService;

    private final AudioTranscriptionClient transcriptionClient;

    private final AudioTranscriptionProperties transcriptionProperties;

    public AiHealthIndicator(
            AiHealthCheckService healthCheckService,
            AiTextGenerationClient client,
            AiHealthProperties properties,
            AudioTranscriptionHealthCheckService transcriptionHealthCheckService,
            AudioTranscriptionClient transcriptionClient,
            AudioTranscriptionProperties transcriptionProperties) {
        this.healthCheckService = healthCheckService;
        this.client = client;
        this.properties = properties;
        this.transcriptionHealthCheckService = transcriptionHealthCheckService;
        this.transcriptionClient = transcriptionClient;
        this.transcriptionProperties = transcriptionProperties;
    }

    @Override
    public Health health() {
        AiHealthCheckResult result = healthCheckService.check();
        AudioTranscriptionHealthCheckResult transcriptionResult =
                transcriptionHealthCheckService.check();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", client.providerName());
        details.put("model", client.modelName());
        details.put("enabled", properties.isEnabled());
        details.put("reachable", result.reachable());
        details.put("latencyMs", result.latencyMs());
        details.put("lastCheckedAt", result.checkedAt().toString());
        if (result.errorType() != null) {
            details.put("errorType", result.errorType().name());
        }
        if (result.httpStatus() != null) {
            details.put("httpStatus", result.httpStatus());
        }
        if (result.providerStatus() != null && !result.providerStatus().isBlank()) {
            details.put("providerStatus", result.providerStatus());
        }

        Map<String, Object> transcription = new LinkedHashMap<>();
        transcription.put("provider", transcriptionClient.providerName());
        transcription.put("model", transcriptionClient.modelName());
        transcription.put("enabled", transcriptionProperties.isEnabled());
        transcription.put("reachable", transcriptionResult.reachable());
        transcription.put("latencyMs", transcriptionResult.latencyMs());
        transcription.put("lastCheckedAt", transcriptionResult.checkedAt().toString());
        if (transcriptionResult.errorType() != null) {
            transcription.put("errorType", transcriptionResult.errorType().name());
        }
        details.put("transcription", transcription);

        return result.up() && transcriptionResult.up()
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
