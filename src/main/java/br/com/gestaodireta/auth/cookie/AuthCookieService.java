package br.com.gestaodireta.auth.cookie;

import br.com.gestaodireta.shared.constant.AppConstants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    private final String cookieName;

    private final boolean secure;

    private final String sameSite;

    public AuthCookieService(
            @Value("${app.cookie.name:" + AppConstants.AUTH_COOKIE_NAME + "}") String cookieName,
            @Value("${app.cookie.secure:false}") boolean secure,
            @Value("${app.cookie.same-site:Lax}") String sameSite) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public Optional<String> extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public String createCookieHeader(String token, long maxAgeSeconds) {
        return buildCookie(token, Duration.ofSeconds(maxAgeSeconds)).toString();
    }

    public String createExpiredCookieHeader() {
        return buildCookie("", Duration.ZERO).toString();
    }

    public String getCookieName() {
        return cookieName;
    }

    public void addCookie(HttpHeaders headers, String token, long maxAgeSeconds) {
        headers.add(HttpHeaders.SET_COOKIE, createCookieHeader(token, maxAgeSeconds));
    }

    public void expireCookie(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, createExpiredCookieHeader());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
