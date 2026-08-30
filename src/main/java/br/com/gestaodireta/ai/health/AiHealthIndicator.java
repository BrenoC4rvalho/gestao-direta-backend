package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.config.AiHealthProperties;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
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

    public AiHealthIndicator(
            AiHealthCheckService healthCheckService,
            AiTextGenerationClient client,
            AiHealthProperties properties) {
        this.healthCheckService = healthCheckService;
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Health health() {
        AiHealthCheckResult result = healthCheckService.check();
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

        return result.up()
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
