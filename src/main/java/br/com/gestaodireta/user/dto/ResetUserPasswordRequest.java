package br.com.gestaodireta.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetUserPasswordRequest(
        @NotBlank
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                        message =
                                "Password must have at least 8 characters, uppercase, lowercase,"
                                        + " number and special character")
                String newPassword) {}
