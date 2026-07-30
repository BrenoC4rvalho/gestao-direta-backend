package br.com.gestaodireta.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywayPhoneMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("flyway_phone_test")
                    .withUsername("gestaodireta")
                    .withPassword("secret");

    @Test
    void shouldMigrateLegacyUsersWithoutPhoneAndPreserveExistingPhones() throws SQLException {
        flyway("16").migrate();

        long userWithoutContact = insertUser("without-contact@example.com");
        long userWithBlankPhone = insertUser("blank-phone@example.com");
        long userWithPhone = insertUser("phone@example.com");
        insertContact(userWithBlankPhone, null);
        insertContact(userWithPhone, "+5524999999999");

        Flyway currentFlyway = flyway(null);
        currentFlyway.migrate();
        currentFlyway.validate();

        assertThat(phoneNumberFor(userWithoutContact)).startsWith("LEGACY_");
        assertThat(phoneNumberFor(userWithBlankPhone)).startsWith("LEGACY_");
        assertThat(phoneNumberFor(userWithPhone)).isEqualTo("+5524999999999");
        assertThat(queryForLong("SELECT count(*) FROM users"))
                .isEqualTo(queryForLong("SELECT count(*) FROM user_contacts"));
        assertThat(
                        queryForLong(
                                "SELECT count(*) FROM user_contacts "
                                        + "WHERE phone_number IS NULL OR btrim(phone_number) = ''"))
                .isZero();
        assertThat(queryForLong("SELECT count(*) FROM flyway_schema_history WHERE success = false"))
                .isZero();
    }

    private Flyway flyway(String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .locations("classpath:db/migration");

        if (target != null) {
            configuration.target(target);
        }

        return configuration.load();
    }

    private long insertUser(String email) throws SQLException {
        String sql =
                """
                INSERT INTO users (name, email, password, user_type, status, created_at)
                VALUES ('Legacy user', ?, 'password', 'USER', 'ACTIVE', CURRENT_TIMESTAMP)
                RETURNING id
                """;

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void insertContact(long userId, String phoneNumber) throws SQLException {
        String sql =
                """
                INSERT INTO user_contacts (
                    user_id, phone_number, phone_verification_status, preferred_channel,
                    status, created_at
                )
                VALUES (?, ?, 'NOT_INFORMED', 'NONE', 'PENDING', CURRENT_TIMESTAMP)
                """;

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, phoneNumber);
            statement.executeUpdate();
        }
    }

    private String phoneNumberFor(long userId) throws SQLException {
        String sql = "SELECT phone_number FROM user_contacts WHERE user_id = ?";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private long queryForLong(String sql) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
