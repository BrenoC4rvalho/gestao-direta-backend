package br.com.gestaodireta.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordRecoveryResetRequest(
        @NotBlank String recoveryToken,
        @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")
                String newPassword,
        @NotBlank String confirmPassword) {}
