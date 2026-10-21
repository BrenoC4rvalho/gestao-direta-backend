package br.com.gestaodireta.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class JwtServiceConfigurationTest {

    @Test
    void shouldRejectTheKnownDevelopmentSigningSecret() {
        assertThatThrownBy(
                        () ->
                                new JwtService(
                                        "issuer",
                                        "change-this-secret-in-production-change-this-secret",
                                        120,
                                        15,
                                        Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT signing secret must be configured securely");
    }
}
