package br.com.gestaodireta.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.auth.cookie.AuthCookieService;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

    @Test
    void shouldNotConvertAuthenticationInfrastructureFailuresIntoUnauthorizedResponses()
            throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        DecodedJWT decodedJwt = mock(DecodedJWT.class);
        when(jwtService.verify("valid-token")).thenReturn(decodedJwt);
        when(decodedJwt.getSubject()).thenReturn("42");
        when(userDetailsService.loadUserById(42L))
                .thenThrow(new IllegalStateException("database unavailable"));

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(
                        new AuthCookieService("gd_session", false, "Lax"),
                        jwtService,
                        userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/financial/summary");
        request.setCookies(new Cookie("gd_session", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void shouldNotConvertControllerRuntimeExceptionsIntoUnauthorizedResponses()
            throws ServletException, IOException {
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(
                        new AuthCookieService("gd_session", false, "Lax"),
                        mock(JwtService.class),
                        mock(CustomUserDetailsService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/financial/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain =
                (servletRequest, servletResponse) -> {
                    throw new IllegalStateException("controller failure");
                };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("controller failure");
    }
}
