package br.com.gestaodireta.shared.security;

import br.com.gestaodireta.shared.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtils {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String ROLE_USER = "ROLE_USER";

    private SecurityUtils() {}

    public static Authentication getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required");
        }

        return authentication;
    }

    public static Long getAuthenticatedUserId() {
        String name = getAuthenticatedUser().getName();

        try {
            return Long.valueOf(name);
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException("Authenticated user id is invalid");
        }
    }

    public static String getAuthenticatedEmail() {
        Object principal = getAuthenticatedUser().getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        return getAuthenticatedUser().getName();
    }

    public static boolean isAdmin() {
        return hasAuthority(ROLE_ADMIN);
    }

    public static boolean isUser() {
        return hasAuthority(ROLE_USER);
    }

    private static boolean hasAuthority(String authority) {
        Authentication authentication = getAuthenticatedUser();

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
