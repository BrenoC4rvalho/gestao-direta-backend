package br.com.gestaodireta.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.system.dto.SystemStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemStatusServiceTest {

    @Test
    void shouldReturnUpWhenDatabaseIsAvailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Environment environment = mock(Environment.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        SystemStatusService service =
                new SystemStatusService(jdbcTemplate, environment, "gestao-direta-api");

        SystemStatusResponse response = service.getStatus();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.database()).isEqualTo("UP");
        assertThat(response.profile()).isEqualTo("test");
        assertThat(response.uptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void shouldReturnDegradedWhenDatabaseIsUnavailableWithoutThrowingError() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Environment environment = mock(Environment.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("connection failed"));
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        SystemStatusService service =
                new SystemStatusService(jdbcTemplate, environment, "gestao-direta-api");

        SystemStatusResponse response = service.getStatus();

        assertThat(response.status()).isEqualTo("DEGRADED");
        assertThat(response.database()).isEqualTo("DOWN");
        assertThat(response.uptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void shouldReturnDefaultProfileWhenNoProfileIsActive() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Environment environment = mock(Environment.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(environment.getActiveProfiles()).thenReturn(new String[] {});
        SystemStatusService service =
                new SystemStatusService(jdbcTemplate, environment, "gestao-direta-api");

        SystemStatusResponse response = service.getStatus();

        assertThat(response.profile()).isEqualTo("default");
    }

    @Test
    void shouldJoinMultipleActiveProfiles() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Environment environment = mock(Environment.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test", "local"});
        SystemStatusService service =
                new SystemStatusService(jdbcTemplate, environment, "gestao-direta-api");

        SystemStatusResponse response = service.getStatus();

        assertThat(response.profile()).isEqualTo("test,local");
    }
}
