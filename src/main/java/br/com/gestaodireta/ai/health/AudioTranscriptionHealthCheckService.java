package br.com.gestaodireta.ai.health;

import br.com.gestaodireta.ai.config.AiHealthProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AudioTranscriptionHealthCheckService {

    private final AudioTranscriptionClient client;
    private final AudioTranscriptionProperties transcriptionProperties;
    private final AiHealthProperties healthProperties;
    private final Clock clock;
    private volatile AudioTranscriptionHealthCheckResult cachedResult;

    public AudioTranscriptionHealthCheckService(
            AudioTranscriptionClient client,
            AudioTranscriptionProperties transcriptionProperties,
            AiHealthProperties healthProperties,
            Clock clock) {
        this.client = client;
        this.transcriptionProperties = transcriptionProperties;
        this.healthProperties = healthProperties;
        this.clock = clock;
    }

    public AudioTranscriptionHealthCheckResult check() {
        AudioTranscriptionHealthCheckResult current = cachedResult;
        if (isCurrent(current)) {
            return current;
        }
        synchronized (this) {
            current = cachedResult;
            if (isCurrent(current)) {
                return current;
            }
            AudioTranscriptionHealthCheckResult checked = performCheck();
            cachedResult = checked;
            return checked;
        }
    }

    private AudioTranscriptionHealthCheckResult performCheck() {
        Instant checkedAt = clock.instant();
        if (!healthProperties.isEnabled() || !transcriptionProperties.isEnabled()) {
            return new AudioTranscriptionHealthCheckResult(true, false, 0, checkedAt, null);
        }
        long startNanos = System.nanoTime();
        try {
            client.probe();
            return new AudioTranscriptionHealthCheckResult(
                    true, true, elapsedMillis(startNanos), checkedAt, null);
        } catch (AudioTranscriptionException exception) {
            return new AudioTranscriptionHealthCheckResult(
                    false,
                    exception.getReason() != AudioTranscriptionException.Reason.TIMEOUT,
                    elapsedMillis(startNanos),
                    checkedAt,
                    exception.getReason());
        } catch (RuntimeException exception) {
            return new AudioTranscriptionHealthCheckResult(
                    false,
                    false,
                    elapsedMillis(startNanos),
                    checkedAt,
                    AudioTranscriptionException.Reason.PROVIDER_ERROR);
        }
    }

    private boolean isCurrent(AudioTranscriptionHealthCheckResult result) {
        return result != null
                && result.checkedAt()
                        .plus(Duration.ofSeconds(healthProperties.getCacheSeconds()))
                        .isAfter(clock.instant());
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
