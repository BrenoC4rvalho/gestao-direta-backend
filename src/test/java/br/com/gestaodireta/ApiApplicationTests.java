package br.com.gestaodireta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApiApplicationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gestaodireta_test")
                    .withUsername("gestaodireta")
                    .withPassword("secret");

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoadsAndCreatesInitialAdminUser() {
        User admin = userRepository.findByEmail("admin@gestaodireta.com").orElseThrow();

        assertThat(admin.getName()).isEqualTo("Administrador");
        assertThat(admin.getEmail()).isEqualTo("admin@gestaodireta.com");
        assertThat(admin.getPassword()).isNotEqualTo("Admin@123");
        assertThat(passwordEncoder.matches("Admin@123", admin.getPassword())).isTrue();
        assertThat(admin.getDocument()).isNull();
        assertThat(admin.getUserType()).isEqualTo(UserType.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(admin.getUpdatedAt()).isNull();
    }
}
