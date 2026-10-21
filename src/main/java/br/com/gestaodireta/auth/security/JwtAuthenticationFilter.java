package br.com.gestaodireta.auth.security;

import br.com.gestaodireta.auth.cookie.AuthCookieService;
import br.com.gestaodireta.user.enumeration.UserStatus;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthCookieService authCookieService;

    private final JwtService jwtService;

    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(
            AuthCookieService authCookieService,
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService) {
        this.authCookieService = authCookieService;
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            authenticate(request);
        } catch (AuthenticationException
                | JWTVerificationException
                | NumberFormatException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath());
    }

    private void authenticate(HttpServletRequest request) {
        authCookieService
                .extractToken(request)
                .ifPresent(token -> authenticateToken(token, request));
    }

    private void authenticateToken(String token, HttpServletRequest request) {
        DecodedJWT decodedJwt = jwtService.verify(token);
        Long userId = Long.valueOf(decodedJwt.getSubject());
        CustomUserDetails userDetails = customUserDetailsService.loadUserById(userId);

        if (!UserStatus.ACTIVE.equals(userDetails.getUser().getStatus())) {
            throw new DisabledException("User is not active");
        }

        if (decodedJwt.getClaim("credentialsVersion").asInt()
                != userDetails.getUser().getCredentialsVersion()) {
            throw new BadCredentialsException("Credentials have changed");
        }

        UserAuthenticationToken authentication =
                new UserAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
