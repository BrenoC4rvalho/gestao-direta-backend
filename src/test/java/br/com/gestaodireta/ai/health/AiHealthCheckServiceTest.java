package br.com.gestaodireta.ai.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.ai.config.AiHealthProperties;
import br.com.gestaodireta.ai.service.AiProviderException;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiHealthCheckServiceTest {

    @Test
    void shouldCacheSuccessfulProbeUntilTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T00:00:00Z"));
        AiTextGenerationClient client = Mockito.mock(AiTextGenerationClient.class);
        doNothing().when(client).probe();
        AiHealthCheckService service = new AiHealthCheckService(client, properties(), clock);

        assertThat(service.check().up()).isTrue();
        assertThat(service.check().up()).isTrue();
        verify(client, times(1)).probe();

        clock.advanceSeconds(61);
        assertThat(service.check().up()).isTrue();
        verify(client, times(2)).probe();
    }

    @Test
    void shouldReportProviderFailureWithoutThrowing() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T00:00:00Z"));
        AiTextGenerationClient client = Mockito.mock(AiTextGenerationClient.class);
        when(client.providerName()).thenReturn("gemini");
        when(client.modelName()).thenReturn("gemini-3.1-flash-lite");
        doThrow(
                        new AiProviderException(
                                AiProviderException.Reason.INVALID_REQUEST,
                                400,
                                "gemini",
                                "gemini-3.1-flash-lite",
                                "INVALID_ARGUMENT",
                                "Invalid request",
                                null))
                .when(client)
                .probe();
        AiHealthCheckService service = new AiHealthCheckService(client, properties(), clock);

        AiHealthCheckResult result = service.check();

        assertThat(result.up()).isFalse();
        assertThat(result.reachable()).isTrue();
        assertThat(result.errorType()).isEqualTo(AiProviderException.Reason.INVALID_REQUEST);
        assertThat(result.httpStatus()).isEqualTo(400);
        assertThat(result.providerStatus()).isEqualTo("INVALID_ARGUMENT");
    }

    private AiHealthProperties properties() {
        AiHealthProperties properties = new AiHealthProperties();
        properties.setCacheSeconds(60);
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
