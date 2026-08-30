package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.service.AiProviderException;
import java.time.Instant;

public record AiHealthCheckResult(
        boolean up,
        boolean reachable,
        long latencyMs,
        Instant checkedAt,
        AiProviderException.Reason errorType,
        Integer httpStatus,
        String providerStatus) {}
