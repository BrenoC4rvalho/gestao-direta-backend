package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import java.time.Instant;

public record AudioTranscriptionHealthCheckResult(
        boolean up,
        boolean reachable,
        long latencyMs,
        Instant checkedAt,
        AudioTranscriptionException.Reason errorType) {}
