package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                        message =
                                "must have at least 8 characters, including uppercase, lowercase,"
                                        + " number and special character")
                String password,
        @Size(max = 20) String document,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotNull UserType userType) {}
