package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.config.AiHealthProperties;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiHealthCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiHealthCheckService.class);

    private final AiTextGenerationClient client;

    private final AiHealthProperties properties;

    private final Clock clock;

    private volatile AiHealthCheckResult cachedResult;

    public AiHealthCheckService(
            AiTextGenerationClient client, AiHealthProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public AiHealthCheckResult check() {
        AiHealthCheckResult current = cachedResult;
        if (isCurrent(current)) {
            return current;
        }

        synchronized (this) {
            current = cachedResult;
            if (isCurrent(current)) {
                return current;
            }
            AiHealthCheckResult checked = performCheck();
            cachedResult = checked;
            return checked;
        }
    }

    private AiHealthCheckResult performCheck() {
        Instant checkedAt = clock.instant();
        if (!properties.isEnabled()) {
            return new AiHealthCheckResult(true, false, 0, checkedAt, null, null, null);
        }

        long startNanos = System.nanoTime();
        try {
            client.probe();
            return new AiHealthCheckResult(
                    true, true, elapsedMillis(startNanos), checkedAt, null, null, null);
        } catch (AiProviderException exception) {
            return new AiHealthCheckResult(
                    false,
                    exception.getReason() != AiProviderException.Reason.SERVICE_UNAVAILABLE
                            && exception.getReason() != AiProviderException.Reason.TIMEOUT,
                    elapsedMillis(startNanos),
                    checkedAt,
                    exception.getReason(),
                    exception.getStatusCode(),
                    exception.getProviderStatus());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "AI health probe failed unexpectedly: provider={} model={}",
                    client.providerName(),
                    client.modelName(),
                    exception);
            return new AiHealthCheckResult(
                    false,
                    false,
                    elapsedMillis(startNanos),
                    checkedAt,
                    AiProviderException.Reason.SERVICE_UNAVAILABLE,
                    null,
                    null);
        }
    }

    private boolean isCurrent(AiHealthCheckResult result) {
        return result != null
                && result.checkedAt()
                        .plus(Duration.ofSeconds(properties.getCacheSeconds()))
                        .isAfter(clock.instant());
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
