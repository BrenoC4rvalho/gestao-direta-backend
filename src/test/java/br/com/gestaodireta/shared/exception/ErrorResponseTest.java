package br.com.gestaodireta.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void shouldCreateErrorResponseWithExpectedFields() {
        LocalDateTime timestamp = LocalDateTime.now();

        ErrorResponse response =
                new ErrorResponse(
                        timestamp,
                        400,
                        "Bad Request",
                        "Invalid request",
                        "/api/test",
                        List.of("name: must not be blank"));

        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.message()).isEqualTo("Invalid request");
        assertThat(response.path()).isEqualTo("/api/test");
        assertThat(response.details()).containsExactly("name: must not be blank");
    }
}
