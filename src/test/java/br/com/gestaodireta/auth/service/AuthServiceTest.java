package br.com.gestaodireta.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.auth.dto.ChangePasswordRequest;
import br.com.gestaodireta.auth.dto.LoginRequest;
import br.com.gestaodireta.auth.security.CustomUserDetails;
import br.com.gestaodireta.auth.security.UserAuthenticationToken;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.UnauthorizedException;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class AuthServiceTest extends PostgresIntegrationTest {

    @Autowired private AuthService authService;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
    }

    @Test
    void shouldLoginActiveUser() {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);

        AuthService.LoginResult result =
                authService.login(new LoginRequest("active@example.com", "Strong1!"));

        assertThat(result.token()).isNotBlank();
        assertThat(result.response().user().id()).isEqualTo(user.getId());
        assertThat(result.response().user().email()).isEqualTo("active@example.com");
    }

    @Test
    void shouldRejectInactiveUserLogin() {
        saveUser("Inactive User", "inactive@example.com", UserType.USER, UserStatus.INACTIVE);

        assertThatThrownBy(
                        () ->
                                authService.login(
                                        new LoginRequest("inactive@example.com", "Strong1!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void shouldRejectBlockedUserLogin() {
        saveUser("Blocked User", "blocked@example.com", UserType.USER, UserStatus.BLOCKED);

        assertThatThrownBy(
                        () ->
                                authService.login(
                                        new LoginRequest("blocked@example.com", "Strong1!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void shouldRejectWrongPasswordLogin() {
        saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);

        assertThatThrownBy(
                        () -> authService.login(new LoginRequest("active@example.com", "Wrong1!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void shouldChangePasswordWhenCurrentPasswordIsCorrect() {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        authenticateAs(user);

        authService.changePassword(new ChangePasswordRequest("Strong1!", "Changed1!"));

        User savedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("Changed1!", savedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Strong1!", savedUser.getPassword())).isFalse();
    }

    @Test
    void shouldRejectChangePasswordWhenCurrentPasswordIsWrong() {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        authenticateAs(user);

        assertThatThrownBy(
                        () ->
                                authService.changePassword(
                                        new ChangePasswordRequest("Wrong1!", "Changed1!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void shouldRejectChangePasswordWhenNewPasswordMatchesCurrentPassword() {
        User user = saveUser("Active User", "active@example.com", UserType.USER, UserStatus.ACTIVE);
        authenticateAs(user);

        assertThatThrownBy(
                        () ->
                                authService.changePassword(
                                        new ChangePasswordRequest("Strong1!", "Strong1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("New password must be different from current password");
    }

    @Test
    void shouldUseOnlyGlobalAdminOrUserAuthorities() {
        User admin = saveUser("Admin User", "admin@example.com", UserType.ADMIN, UserStatus.ACTIVE);
        User common = saveUser("Common User", "user@example.com", UserType.USER, UserStatus.ACTIVE);

        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        CustomUserDetails commonDetails = new CustomUserDetails(common);

        assertThat(authorities(adminDetails)).containsExactly("ROLE_ADMIN");
        assertThat(authorities(commonDetails)).containsExactly("ROLE_USER");
    }

    private List<String> authorities(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private void authenticateAs(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UserAuthenticationToken authentication =
                new UserAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
