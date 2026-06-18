package br.com.gestaodireta.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.shared.exception.UnauthorizedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

class SecurityUtilsTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenAuthenticationIsMissing() {
        assertThatThrownBy(SecurityUtils::getAuthenticatedUser)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void shouldReturnAuthenticatedUserId() {
        setAuthentication("42", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Long userId = SecurityUtils.getAuthenticatedUserId();

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenAuthenticatedUserIdIsInvalid() {
        setAuthentication("invalid", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThatThrownBy(SecurityUtils::getAuthenticatedUserId)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authenticated user id is invalid");
    }

    @Test
    void shouldReturnEmailFromUserDetailsPrincipal() {
        User principal =
                new User(
                        "user@example.com",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String email = SecurityUtils.getAuthenticatedEmail();

        assertThat(email).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnTrueWhenUserIsAdmin() {
        setAuthentication("1", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(SecurityUtils.isAdmin()).isTrue();
        assertThat(SecurityUtils.isUser()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUserIsUser() {
        setAuthentication("2", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(SecurityUtils.isUser()).isTrue();
        assertThat(SecurityUtils.isAdmin()).isFalse();
    }

    private void setAuthentication(String name, List<SimpleGrantedAuthority> authorities) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(name, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
