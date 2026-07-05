package br.com.gestaodireta.system.dto;

import java.time.Instant;

public record SystemStatusResponse(
        String status,
        String application,
        String profile,
        String database,
        long uptimeSeconds,
        Instant timestamp) {}
