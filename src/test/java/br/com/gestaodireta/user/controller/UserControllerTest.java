package br.com.gestaodireta.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import br.com.gestaodireta.user.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;

    @Autowired private UserContactRepository userContactRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userContactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUserAsAdmin() throws Exception {
        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        userBody(
                                                "Maria Silva", "maria@example.com", UserType.USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.userType").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldCreateAdminAsAdmin() throws Exception {
        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        userBody(
                                                "Admin User",
                                                "admin-new@example.com",
                                                UserType.ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateUserAsActiveProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        userBody(
                                                "Employee",
                                                "employee-new@example.com",
                                                UserType.USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectAdminCreationAsProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        userBody("Admin", "admin-new@example.com", UserType.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUserCreationWithoutActiveProducerPermission() throws Exception {
        Farm activeFarm = saveFarm("Active Farm", FarmStatus.ACTIVE);
        Farm inactiveFarm = saveFarm("Inactive Farm", FarmStatus.INACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactiveProducer =
                saveUser("Inactive Producer", "inactive-producer@example.com", UserType.USER);
        User noLinkUser = saveUser("No Link", "no-link@example.com", UserType.USER);
        saveFarmUser(activeFarm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(activeFarm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(inactiveFarm, inactiveProducer, FarmUserRole.PRODUCER);

        expectCannotCreateUser(employee);
        expectCannotCreateUser(accountant);
        expectCannotCreateUser(inactiveProducer);
        expectCannotCreateUser(noLinkUser);
    }

    @Test
    void shouldReturnUnauthorizedWhenUserIsNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/users").contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldValidateRequiredFields() throws Exception {
        String body =
                """
                {
                  "email": "invalid",
                  "password": "weak",
                  "userType": null
                }
                """;

        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void shouldRejectWeakPassword() throws Exception {
        String body =
                """
                {
                  "name": "Maria Silva",
                  "email": "maria@example.com",
                  "password": "weakpass",
                  "userType": "USER"
                }
                """;

        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void shouldRejectDuplicatedEmail() throws Exception {
        saveUser("Maria Silva", "maria@example.com", UserType.USER);

        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        userBody(
                                                "Maria Souza", "maria@example.com", UserType.USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already in use"));
    }

    @Test
    void shouldListUsersAsAdmin() throws Exception {
        saveUser("Admin User", "admin@example.com", UserType.ADMIN);
        saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(get("/api/users").contextPath(CONTEXT_PATH).with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void shouldListUsersWithFiltersAsAdmin() throws Exception {
        saveUser("Admin User", "admin@example.com", UserType.ADMIN);
        saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .param("search", "COMMON")
                                .param("userType", "USER")
                                .param("status", "ACTIVE")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("user@example.com"));
    }

    @Test
    void shouldListUsersWithRepeatedStatusFiltersAsAdmin() throws Exception {
        saveUser("Admin User", "admin-list@example.com", UserType.ADMIN);
        saveUser("Common User", "user@example.com", UserType.USER);
        User blockedUser = saveUser("Blocked User", "blocked-user@example.com", UserType.USER);
        blockedUser.setStatus(UserStatus.BLOCKED);
        userRepository.save(blockedUser);
        User inactiveUser = saveUser("Inactive User", "inactive-user@example.com", UserType.USER);
        inactiveUser.setStatus(UserStatus.INACTIVE);
        userRepository.save(inactiveUser);

        mockMvc.perform(
                        get("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .param("userType", "USER")
                                .param("status", "INACTIVE")
                                .param("statuses", "ACTIVE")
                                .param("statuses", "BLOCKED")
                                .param("statuses", "ACTIVE")
                                .param("sort", "email")
                                .param("direction", "ASC")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].email").value("blocked-user@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("user@example.com"));
    }

    @Test
    void shouldRejectInvalidUserFilterEnum() throws Exception {
        mockMvc.perform(
                        get("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .param("userType", "INVALID")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectListUsersWhenAuthenticatedUserIsNotAdmin() throws Exception {
        mockMvc.perform(get("/api/users").contextPath(CONTEXT_PATH).with(user("1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFindUserByIdAsAdmin() throws Exception {
        User savedUser = saveUser("Admin User", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        get("/api/users/{id}", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldRejectFindUserByIdWhenAuthenticatedUserIsNotAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/users/{id}", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFindUserByEmailAsAdmin() throws Exception {
        User savedUser = saveUser("Target User", "target@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/users/search-by-email")
                                .contextPath(CONTEXT_PATH)
                                .param("email", "target@example.com")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldFindUserByEmailAsActiveProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User target = saveUser("Target User", "target@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        get("/api/users/search-by-email")
                                .contextPath(CONTEXT_PATH)
                                .param("email", " TARGET@EXAMPLE.COM ")
                                .with(user(String.valueOf(producer.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.email").value("target@example.com"));
    }

    @Test
    void shouldRejectEmailSearchWithoutActiveProducerPermission() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User employee = saveUser("Employee", "employee@example.com", UserType.USER);
        User accountant = saveUser("Accountant", "accountant@example.com", UserType.USER);
        User inactive = saveUser("Inactive", "inactive@example.com", UserType.USER);
        User noLinkUser = saveUser("No Link", "no-link@example.com", UserType.USER);
        saveFarmUser(farm, employee, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, accountant, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactive, FarmUserRole.INACTIVE);

        expectCannotSearchByEmail(employee);
        expectCannotSearchByEmail(accountant);
        expectCannotSearchByEmail(inactive);
        expectCannotSearchByEmail(noLinkUser);
    }

    @Test
    void shouldRejectEmailSearchWithoutEmail() throws Exception {
        mockMvc.perform(
                        get("/api/users/search-by-email")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is required"));
    }

    @Test
    void shouldRejectEmailSearchWithInvalidEmail() throws Exception {
        mockMvc.perform(
                        get("/api/users/search-by-email")
                                .contextPath(CONTEXT_PATH)
                                .param("email", "invalid")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is invalid"));
    }

    @Test
    void shouldReturnNotFoundWhenEmailDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/users/search-by-email")
                                .contextPath(CONTEXT_PATH)
                                .param("email", "missing@example.com")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void shouldUpdateAuthenticatedUserProfile() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        String body =
                """
                {
                  "name": "Updated User",
                  "document": "98765432100"
                }
                """;

        mockMvc.perform(
                        put("/api/users/me")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(savedUser.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated User"))
                .andExpect(jsonPath("$.document").value("98765432100"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.userType").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldUpdateUserAsAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        put("/api/users/{id}", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateUserBody("Updated User", "98765432100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated User"))
                .andExpect(jsonPath("$.document").value("98765432100"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.userType").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldRejectUserUpdateWhenAuthenticatedUserIsNotAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        put("/api/users/{id}", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateUserBody("Updated User", "98765432100")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUserUpdateWhenAuthenticatedUserIsProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        put("/api/users/{id}", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateUserBody("Updated User", "98765432100")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUserUpdateWithBlankName() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        put("/api/users/{id}", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateUserBody("   ", "98765432100")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingUserDoesNotExist() throws Exception {
        mockMvc.perform(
                        put("/api/users/{id}", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateUserBody("Updated User", "98765432100")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void shouldResetPasswordAsAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        patch("/api/users/{id}/reset-password", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resetPasswordBody("NewPassword@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword@123", updatedUser.getPassword())).isTrue();
    }

    @Test
    void shouldRejectPasswordResetWhenAuthenticatedUserIsNotAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        patch("/api/users/{id}/reset-password", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resetPasswordBody("NewPassword@123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPasswordResetWhenAuthenticatedUserIsProducer() throws Exception {
        Farm farm = saveFarm("Farm", FarmStatus.ACTIVE);
        User producer = saveUser("Producer", "producer@example.com", UserType.USER);
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        saveFarmUser(farm, producer, FarmUserRole.PRODUCER);

        mockMvc.perform(
                        patch("/api/users/{id}/reset-password", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(producer.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resetPasswordBody("NewPassword@123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPasswordResetWithBlankPassword() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        patch("/api/users/{id}/reset-password", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resetPasswordBody("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenResetPasswordUserDoesNotExist() throws Exception {
        mockMvc.perform(
                        patch("/api/users/{id}/reset-password", 999L)
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resetPasswordBody("NewPassword@123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void shouldRejectResettingOwnPasswordAsAdmin() throws Exception {
        User admin = saveUser("Admin User", "admin@example.com", UserType.ADMIN);

        mockMvc.perform(
                        patch("/api/users/{id}/reset-password", admin.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(admin.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resetPasswordBody("NewPassword@123")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Use /auth/change-password to change the authenticated user's own password"));
    }

    @Test
    void shouldReturnAuthenticatedUserProfile() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);

        mockMvc.perform(
                        get("/api/users/me")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(savedUser.getId())).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldUpdateStatusAsAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        String body = objectMapper.writeValueAsString(new StatusBody(UserStatus.BLOCKED));

        mockMvc.perform(
                        patch("/api/users/{id}/status", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void shouldRejectStatusUpdateWhenAuthenticatedUserIsNotAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        String body = objectMapper.writeValueAsString(new StatusBody(UserStatus.BLOCKED));

        mockMvc.perform(
                        patch("/api/users/{id}/status", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUpdateTypeAsAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        String body = objectMapper.writeValueAsString(new TypeBody(UserType.ADMIN));

        mockMvc.perform(
                        patch("/api/users/{id}/type", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("ADMIN"));
    }

    @Test
    void shouldRejectTypeUpdateWhenAuthenticatedUserIsNotAdmin() throws Exception {
        User savedUser = saveUser("Common User", "user@example.com", UserType.USER);
        String body = objectMapper.writeValueAsString(new TypeBody(UserType.ADMIN));

        mockMvc.perform(
                        patch("/api/users/{id}/type", savedUser.getId())
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
    }

    private void expectCannotCreateUser(User authenticatedUser) throws Exception {
        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user(String.valueOf(authenticatedUser.getId())).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        userBody(
                                                "New User",
                                                "new-user-%d@example.com"
                                                        .formatted(authenticatedUser.getId()),
                                                UserType.USER)))
                .andExpect(status().isForbidden());
    }

    private void expectCannotSearchByEmail(User authenticatedUser) throws Exception {
        mockMvc.perform(
                        get("/api/users/search-by-email")
                                .contextPath(CONTEXT_PATH)
                                .param("email", "target@example.com")
                                .with(
                                        user(String.valueOf(authenticatedUser.getId()))
                                                .roles("USER")))
                .andExpect(status().isForbidden());
    }

    private String updateUserBody(String name, String document) {
        return """
                {
                  "name": "%s",
                  "document": "%s"
                }
                """
                .formatted(name, document);
    }

    private String resetPasswordBody(String newPassword) {
        return """
                {
                  "newPassword": "%s"
                }
                """
                .formatted(newPassword);
    }

    private String userBody(String name, String email, UserType userType) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "Strong1!",
                  "document": "12345678900",
                  "phoneNumber": "%s",
                  "userType": "%s"
                }
                """
                .formatted(name, email, phoneNumberFor(email), userType.name());
    }

    private User saveUser(String name, String email, UserType userType) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(userType);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private String phoneNumberFor(String email) {
        int number = Math.floorMod(email.hashCode(), 100_000_000);

        return "+5524" + String.format("%08d", number);
    }

    private Farm saveFarm(String name, FarmStatus status) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(status);

        return farmRepository.save(farm);
    }

    private FarmUser saveFarmUser(Farm farm, User user, FarmUserRole role) {
        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(role);

        return farmUserRepository.save(farmUser);
    }

    private record StatusBody(UserStatus status) {}

    private record TypeBody(UserType userType) {}
}
