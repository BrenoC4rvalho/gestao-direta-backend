package br.com.gestaodireta.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordRecoveryVerifyRequest(
        @NotBlank @Email String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
