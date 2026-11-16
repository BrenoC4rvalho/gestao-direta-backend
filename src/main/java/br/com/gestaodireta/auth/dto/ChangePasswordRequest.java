package br.com.gestaodireta.auth.dto;

import br.com.gestaodireta.user.config.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
                @Pattern(
                        regexp = PasswordPolicy.REGEX,
                        message =
                                "Password must have at least 8 characters, uppercase, lowercase,"
                                        + " number and special character")
                String newPassword) {}
