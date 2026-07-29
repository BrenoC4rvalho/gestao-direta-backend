package br.com.gestaodireta.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email, @NotBlank String password, Boolean rememberMe) {
    public LoginRequest(String email, String password) {
        this(email, password, false);
    }

    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
