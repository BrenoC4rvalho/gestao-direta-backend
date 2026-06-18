package br.com.gestaodireta.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.auth.security.JwtService;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    private static final String COOKIE_NAME = "gd_session";

    @Autowired private MockMvc mockMvc;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldLoginActiveUserAndCreateSessionCookie() throws Exception {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("active@example.com", "Strong1!")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_NAME)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.email").value("active@example.com"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.jwt").doesNotExist());
    }

    @Test
    void shouldRejectInactiveUserLogin() throws Exception {
        saveUser("Inactive User", "inactive@example.com", UserType.USER, UserStatus.INACTIVE);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("inactive@example.com", "Strong1!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBlockedUserLogin() throws Exception {
        saveUser("Blocked User", "blocked@example.com", UserType.USER, UserStatus.BLOCKED);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("blocked@example.com", "Strong1!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWrongPasswordLogin() throws Exception {
        saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("active@example.com", "Wrong1!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogoutAndRemoveCookie() throws Exception {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        Cookie cookie = sessionCookie(user);

        mockMvc.perform(post("/api/auth/logout").contextPath(CONTEXT_PATH).cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_NAME)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void shouldReturnSessionWithValidCookie() throws Exception {
        User user =
                saveUser("Active User", "active@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        Cookie cookie = sessionCookie(user);

        mockMvc.perform(get("/api/auth/session").contextPath(CONTEXT_PATH).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.email").value("active@example.com"))
                .andExpect(jsonPath("$.user.userType").value("ADMIN"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void shouldReturnUnauthorizedWhenSessionHasNoCookie() throws Exception {
        mockMvc.perform(get("/api/auth/session").contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldChangePasswordAndRemoveCookie() throws Exception {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        Cookie cookie = sessionCookie(user);
        String body =
                """
                {
                  "currentPassword": "Strong1!",
                  "newPassword": "Changed1!"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/change-password")
                                .contextPath(CONTEXT_PATH)
                                .cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        User savedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("Changed1!", savedUser.getPassword())).isTrue();
    }

    @Test
    void shouldRejectChangePasswordWithWrongCurrentPassword() throws Exception {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        Cookie cookie = sessionCookie(user);
        String body =
                """
                {
                  "currentPassword": "Wrong1!",
                  "newPassword": "Changed1!"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/change-password")
                                .contextPath(CONTEXT_PATH)
                                .cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWithInvalidJwt() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, "invalid-token");

        mockMvc.perform(get("/api/auth/session").contextPath(CONTEXT_PATH).cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWithExpiredJwt() throws Exception {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        Cookie cookie = new Cookie(COOKIE_NAME, expiredToken(user));

        mockMvc.perform(get("/api/auth/session").contextPath(CONTEXT_PATH).cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotExposeJwtInLoginBody() throws Exception {
        saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);

        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contextPath(CONTEXT_PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody("active@example.com", "Strong1!")))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("eyJ");
    }

    @Test
    void shouldKeepAuthoritiesRestrictedToAdminOrUser() throws Exception {
        User admin = saveUser("Admin User", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        User common = saveUser("Common User", "user@example.com", UserType.USER, UserStatus.ACTIVE);

        String adminToken = jwtService.generateToken(admin);
        String commonToken = jwtService.generateToken(common);

        assertThat(adminToken).isNotBlank();
        assertThat(commonToken).isNotBlank();
        assertThat(admin.getUserType().name()).isEqualTo("ADMIN");
        assertThat(common.getUserType().name()).isEqualTo("USER");
    }

    private Cookie sessionCookie(User user) {
        return new Cookie(COOKIE_NAME, jwtService.generateToken(user));
    }

    private String expiredToken(User user) {
        Instant now = Instant.now();

        return JWT.create()
                .withIssuer("gestao-direta-api-test")
                .withSubject(String.valueOf(user.getId()))
                .withClaim("email", user.getEmail())
                .withClaim("userType", user.getUserType().name())
                .withIssuedAt(now.minusSeconds(3600))
                .withExpiresAt(now.minusSeconds(60))
                .sign(Algorithm.HMAC256("test-secret-test-secret-test-secret-test-secret"));
    }

    private String loginBody(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """
                .formatted(email, password);
    }

    private User saveUser(String name, String email, UserType userType, UserStatus status) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setDocument("12345678900");
        user.setUserType(userType);
        user.setStatus(status);

        return userRepository.save(user);
    }
}
