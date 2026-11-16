package br.com.gestaodireta;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "SPRING_DATASOURCE_HIKARI_POOL_NAME=EnvironmentPool",
            "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=3",
            "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=7",
            "SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=25000",
            "SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=120000",
            "SPRING_DATASOURCE_HIKARI_MAX_LIFETIME=900000"
        })
@ActiveProfiles("test")
@Testcontainers
class HikariEnvironmentOverrideTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gestaodireta_test")
                    .withUsername("gestaodireta")
                    .withPassword("secret");

    @Autowired private HikariDataSource dataSource;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void appliesHikariEnvironmentOverridesBeforeThePoolStarts() {
        assertThat(dataSource.getPoolName()).isEqualTo("EnvironmentPool");
        assertThat(dataSource.getMinimumIdle()).isEqualTo(3);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(7);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(25000L);
        assertThat(dataSource.getIdleTimeout()).isEqualTo(120000L);
        assertThat(dataSource.getMaxLifetime()).isEqualTo(900000L);
    }
}
