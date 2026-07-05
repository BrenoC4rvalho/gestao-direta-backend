package br.com.gestaodireta.system.service;

import br.com.gestaodireta.system.dto.SystemStatusResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String DATABASE_UP = "UP";
    private static final String DATABASE_DOWN = "DOWN";

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final String applicationName;
    private final Instant startedAt;

    public SystemStatusService(
            JdbcTemplate jdbcTemplate,
            Environment environment,
            @Value("${spring.application.name:gestao-direta-api}") String applicationName) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.applicationName = applicationName;
        this.startedAt = Instant.now();
    }

    public SystemStatusResponse getStatus() {
        String databaseStatus = getDatabaseStatus();
        String status = DATABASE_UP.equals(databaseStatus) ? STATUS_UP : STATUS_DEGRADED;
        long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();

        return new SystemStatusResponse(
                status,
                applicationName,
                getActiveProfile(),
                databaseStatus,
                uptimeSeconds,
                Instant.now());
    }

    private String getDatabaseStatus() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            if (Integer.valueOf(1).equals(result)) {
                return DATABASE_UP;
            }

            return DATABASE_DOWN;
        } catch (RuntimeException exception) {
            return DATABASE_DOWN;
        }
    }

    private String getActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();

        if (activeProfiles.length == 0) {
            return "default";
        }

        return String.join(",", activeProfiles);
    }
}
