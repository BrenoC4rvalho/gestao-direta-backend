package br.com.gestaodireta.auth.security;

import java.util.Collection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class UserAuthenticationToken extends UsernamePasswordAuthenticationToken {

    public UserAuthenticationToken(
            CustomUserDetails principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities) {
        super(principal, credentials, authorities);
    }

    @Override
    public String getName() {
        Object principal = getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return String.valueOf(userDetails.getId());
        }

        return super.getName();
    }
}
