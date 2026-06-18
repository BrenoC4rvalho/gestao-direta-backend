package br.com.gestaodireta.user.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
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

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUserAsAdmin() throws Exception {
        String body =
                """
                {
                  "name": "Maria Silva",
                  "email": "maria@example.com",
                  "password": "Strong1!",
                  "document": "12345678900",
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldRejectUserCreationWhenAuthenticatedUserIsNotAdmin() throws Exception {
        String body =
                """
                {
                  "name": "Maria Silva",
                  "email": "maria@example.com",
                  "password": "Strong1!",
                  "userType": "USER"
                }
                """;

        mockMvc.perform(
                        post("/api/users")
                                .contextPath(CONTEXT_PATH)
                                .with(user("1").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
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
        String body =
                """
                {
                  "name": "Maria Souza",
                  "email": "maria@example.com",
                  "password": "Strong1!",
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

    private User saveUser(String name, String email, UserType userType) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(userType);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private record StatusBody(UserStatus status) {}

    private record TypeBody(UserType userType) {}
}
